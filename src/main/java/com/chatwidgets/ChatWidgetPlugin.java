package com.chatwidgets;

import com.chatwidgets.model.FontSize;
import com.chatwidgets.model.MessageCategory;
import com.chatwidgets.model.MessageMergeRule;
import com.chatwidgets.model.MessageVariant;
import com.chatwidgets.model.WidgetMessage;
import com.chatwidgets.overlay.DynamicChatOverlay;
import com.chatwidgets.overlay.OverlayConfig;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.GameState;
import net.runelite.api.MessageNode;
import net.runelite.api.Point;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanID;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ResizeableChanged;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ChatColorConfig;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.chatfilter.ChatFilterConfig;
import net.runelite.client.plugins.chatfilter.ChatFilterPlugin;
import net.runelite.client.plugins.emojis.EmojiPlugin;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Core plugin class. Maintains a single shared message pool ({@link WidgetMessage}) fed by
 * {@code onChatMessage}, and manages a dynamic list of {@link DynamicChatOverlay} instances
 * that each filter and render a subset of the pool based on their {@link OverlayConfig}.
 */
@PluginDescriptor(name = "Chat Widgets", description = "Displays chat messages in customizable overlay widgets.", tags = {
        "game", "private", "public", "clan", "friends", "chat", "pm", "message", "widget", "overlay", "split",
        "move", "custom", "customize", "resizable", "transparent" })
public class ChatWidgetPlugin extends Plugin {

    public static final boolean DEBUG = false;

    private static final String CONFIG_GROUP = "chatwidgets";
    private static final String OVERLAY_CONFIGS_KEY = "overlayConfigs";
    private static final String LEGACY_SHOW_TIMESTAMP_KEY = "showTimestamp";
    private static final String TIMESTAMP_MIGRATED_KEY = "timestampMigratedToPerWidget";
    private static final String LEGACY_FONT_SIZE_KEY = "fontSize";
    private static final String FONT_SIZE_MIGRATED_KEY = "fontSizeMigratedToPerWidget";
    // Keep in sync with build.gradle 'version' on each release (build=standard ignores build.gradle).
    private static final String PLUGIN_VERSION = "1.2.0";
    private static final String LAST_UPDATE_NOTICE_VERSION_KEY = "lastUpdateNoticeVersion";
    private static final int MAX_POOL_SIZE = 200;

    // enum_63: base-36 broadcast link key -> URL (cs2 chat_broadcast_parseurl).
    private static final int BROADCAST_URL_ENUM = 63;
    // Clan id cs2 checks (activeclanchannel_find_affined(2)) before tagging a PvP Arena notification;
    // ClanID has no constant for it.
    private static final int PVP_ARENA_CLAN_ID = 2;
    private static final String PVP_ARENA_TAG = "PvP Arena";

    private static final Pattern BOSS_KC_PATTERN = Pattern.compile("Your .+ count is:");
    private static final MessageMergeRule[] MESSAGE_MERGE_RULES = {
            new MessageMergeRule("You eat", "It heals some health.", true),
            new MessageMergeRule("You drink", Pattern.compile("You have [0-9].*")),
            new MessageMergeRule("You drink", "You have finished your potion.", true),
            new MessageMergeRule("You are now a guest of", "To talk, start each", false),
//            new MessageMergeRule("Now talking in chat-channel", "To talk, start each", false),

    };

    private static final Set<ChatMessageType> SENDER_TYPES = EnumSet.of(
            ChatMessageType.PRIVATECHAT,
            ChatMessageType.PRIVATECHATOUT,
            ChatMessageType.MODPRIVATECHAT,
            ChatMessageType.PUBLICCHAT,
            ChatMessageType.MODCHAT,
            ChatMessageType.AUTOTYPER,
            ChatMessageType.MODAUTOTYPER,
            ChatMessageType.FRIENDSCHAT,
            ChatMessageType.CLAN_CHAT,
            ChatMessageType.CLAN_GUEST_CHAT,
            ChatMessageType.CLAN_GIM_CHAT
    );

    /**
     * Chat types the RuneLite Emojis plugin converts (mirrors its own gate in EmojiPlugin). For
     * these we seed the stored body from the live message node instead of the immutable event
     * message snapshot, so emoji {@code <img=N>} tags inserted by that plugin reach our widgets.
     * All nine are also {@link #SENDER_TYPES}, so reconciliation rebuilds via
     * {@link WidgetMessage#senderMessage}.
     */
    private static final Set<ChatMessageType> EMOJI_WATCHED_TYPES = EnumSet.of(
            ChatMessageType.PUBLICCHAT,
            ChatMessageType.MODCHAT,
            ChatMessageType.FRIENDSCHAT,
            ChatMessageType.CLAN_CHAT,
            ChatMessageType.CLAN_GUEST_CHAT,
            ChatMessageType.CLAN_GIM_CHAT,
            ChatMessageType.PRIVATECHAT,
            ChatMessageType.PRIVATECHATOUT,
            ChatMessageType.MODPRIVATECHAT
    );

    /** All message types the plugin will capture into the shared pool. */
    private static final Set<ChatMessageType> ALL_SUPPORTED_TYPES = EnumSet.of(
            // Game
            ChatMessageType.GAMEMESSAGE, ChatMessageType.SPAM, ChatMessageType.CONSOLE,
            ChatMessageType.WELCOME, ChatMessageType.BROADCAST, ChatMessageType.DIDYOUKNOW,
            ChatMessageType.ENGINE, ChatMessageType.UNKNOWN,
            ChatMessageType.PLAYERRELATED, ChatMessageType.SNAPSHOTFEEDBACK,
            ChatMessageType.FRIENDNOTIFICATION, ChatMessageType.FRIENDSCHATNOTIFICATION,
            ChatMessageType.IGNORENOTIFICATION,
            ChatMessageType.ITEM_EXAMINE, ChatMessageType.NPC_EXAMINE, ChatMessageType.OBJECT_EXAMINE,
            ChatMessageType.CLAN_MESSAGE, ChatMessageType.CLAN_GUEST_MESSAGE,
            ChatMessageType.CLAN_GIM_MESSAGE, ChatMessageType.CLAN_CREATION_INVITATION,
            // Trade
            ChatMessageType.TRADE, ChatMessageType.TRADE_SENT, ChatMessageType.TRADEREQ,
            ChatMessageType.CHALREQ_TRADE, ChatMessageType.CHALREQ_FRIENDSCHAT,
            // Public
            ChatMessageType.PUBLICCHAT, ChatMessageType.MODCHAT,
            ChatMessageType.AUTOTYPER, ChatMessageType.MODAUTOTYPER,
            // Friends chat
            ChatMessageType.FRIENDSCHAT,
            // Clan
            ChatMessageType.CLAN_CHAT, ChatMessageType.CLAN_GUEST_CHAT, ChatMessageType.CLAN_GIM_CHAT,
            ChatMessageType.CHALREQ_CLANCHAT,
            // Private
            ChatMessageType.PRIVATECHAT, ChatMessageType.PRIVATECHATOUT, ChatMessageType.MODPRIVATECHAT,
            ChatMessageType.LOGINLOGOUTNOTIFICATION
    );

    @Inject
    private Client client;

    @Inject
    private ChatWidgetConfig config;

    @Inject
    private ConfigManager configManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ChatColorConfig chatColorConfig;

    @Inject
    private Gson gson;

    @Inject
    private PluginManager pluginManager;

    @Inject
    private ColorPickerManager colorPickerManager;

    // Shared message pool
    private final CopyOnWriteArrayList<WidgetMessage> messages = new CopyOnWriteArrayList<>();

    // Mirrors the Chat Filter plugin's word/regex lists when "Use Chat Filter" is enabled
    private final ChatMessageFilter chatMessageFilter = new ChatMessageFilter();
    private Plugin chatFilterPlugin;
    private Plugin emojiPlugin;

    // Dynamic overlays
    private final List<OverlayConfig> overlayConfigs = new ArrayList<>();
    private final List<DynamicChatOverlay> overlays = new ArrayList<>();

    private NavigationButton navButton;
    private ChatWidgetPanel panel;
    private boolean pmWidgetsHidden = false;
    private boolean firstRun = false;
    private boolean updateNoticePending = false;

    // Messages whose backing MessageNode may be rewritten in place by another plugin after we
    // capture it — Chat Commands (command results) or the RuneLite Emojis plugin (<img=N> tags).
    // Drained on subsequent game ticks by reconcilePendingUpdates().
    private final List<PendingMessageUpdate> pendingUpdates = new ArrayList<>();

    // The Chat Commands plugin rewrites a command's result into the RuneLite-format message; it can
    // take several ticks to arrive, so command entries linger until then.
    private static final int COMMAND_UPDATE_TICKS = 10;
    private static final Function<MessageNode, String> COMMAND_VALUE = MessageNode::getRuneLiteFormatMessage;

    // The Emojis plugin converts shortcuts to <img=N> synchronously within the same dispatch, so a
    // single follow-up tick suffices before the entry is dropped.
    private static final int EMOJI_UPDATE_TICKS = 1;
    private static final Function<MessageNode, String> EMOJI_VALUE = node -> {
        String value = node.getValue();
        return value == null ? null : value.trim();
    };

    /**
     * A pooled message whose backing {@link MessageNode} may be rewritten in place by another
     * plugin after capture. Reconciled on subsequent ticks by {@link #reconcilePendingUpdates()}:
     * once {@code valueAccessor} reports a value different from {@code originalText}, the pooled
     * message is rebuilt with it; the entry is dropped when it updates or after its tick budget.
     */
    private static class PendingMessageUpdate {
        final WidgetMessage widgetMessage;
        final MessageNode messageNode;
        final Function<MessageNode, String> valueAccessor;
        final String originalText;
        int ticksRemaining;

        PendingMessageUpdate(WidgetMessage widgetMessage, MessageNode messageNode,
                Function<MessageNode, String> valueAccessor, String originalText, int ticksRemaining) {
            this.widgetMessage = widgetMessage;
            this.messageNode = messageNode;
            this.valueAccessor = valueAccessor;
            this.originalText = originalText;
            this.ticksRemaining = ticksRemaining;
        }
    }

    @Override
    protected void startUp() {
        loadOverlayConfigs();
        migrateTimestampSetting();
        migrateFontSizeSetting();
        armUpdateNotice();
        rebuildChatFilter();

        for (OverlayConfig oc : overlayConfigs) {
            addOverlay(oc);
        }

        updatePmWidgetVisibility();

        panel = new ChatWidgetPanel(this);
        BufferedImage icon;
        try {
            icon = ImageUtil.loadImageResource(getClass(), "/panelicon.png");
        } catch (Exception e) {
            icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        }
        navButton = NavigationButton.builder()
                .tooltip("Chat Widgets")
                .icon(icon)
                .priority(10)
                .panel(panel)
                .build();
        if (!config.hideSidePanel()) {
            clientToolbar.addNavigation(navButton);
        }
    }

    @Override
    protected void shutDown() {
        for (DynamicChatOverlay overlay : overlays) {
            overlayManager.remove(overlay);
        }
        overlays.clear();
        pendingUpdates.clear();

        if (pmWidgetsHidden) {
            setPmWidgetsHidden(false);
        }

        if (navButton != null) {
            clientToolbar.removeNavigation(navButton);
        }
    }

    // --- Overlay config management ---

    public List<OverlayConfig> getOverlayConfigs() {
        return overlayConfigs;
    }

    public ColorPickerManager getColorPickerManager() {
        return colorPickerManager;
    }

    public void addNewOverlay() {
        OverlayConfig oc = new OverlayConfig();
        overlayConfigs.add(oc);
        addOverlay(oc);
        saveOverlayConfigs();
        if (panel != null) {
            panel.rebuild();
        }
    }

    public void removeOverlay(OverlayConfig oc) {
        overlayConfigs.remove(oc);
        DynamicChatOverlay toRemove = null;
        for (DynamicChatOverlay overlay : overlays) {
            if (overlay.getOverlayConfig() == oc) {
                toRemove = overlay;
                break;
            }
        }
        if (toRemove != null) {
            overlayManager.remove(toRemove);
            overlays.remove(toRemove);
        }
        saveOverlayConfigs();
        updatePmWidgetVisibility();
        if (panel != null) {
            panel.rebuild();
        }
    }

    /**
     * Deletes every existing widget and restores the two defaults created on first install
     * (same set and order as {@link #loadOverlayConfigs()}). Destructive — callers should confirm.
     */
    public void resetOverlays() {
        for (DynamicChatOverlay overlay : overlays) {
            overlayManager.remove(overlay);
        }
        overlays.clear();
        overlayConfigs.clear();

        overlayConfigs.add(OverlayConfig.defaultPrivateWidget());
        overlayConfigs.add(OverlayConfig.defaultAllWidget());
        for (OverlayConfig oc : overlayConfigs) {
            addOverlay(oc);
        }

        saveOverlayConfigs();
        updatePmWidgetVisibility();
        if (panel != null) {
            panel.rebuild();
        }
    }

    public void onOverlayConfigChanged() {
        saveOverlayConfigs();
        updatePmWidgetVisibility();
        for (DynamicChatOverlay overlay : overlays) {
            overlay.setOverlayConfig(overlay.getOverlayConfig());
        }
    }

    public void moveOverlay(OverlayConfig oc, int direction) {
        int index = overlayConfigs.indexOf(oc);
        int newIndex = index + direction;
        if (newIndex < 0 || newIndex >= overlayConfigs.size()) {
            return;
        }
        Collections.swap(overlayConfigs, index, newIndex);
        Collections.swap(overlays, index, newIndex);
        refreshOverlayPriorities();
        saveOverlayConfigs();
        if (panel != null) {
            panel.rebuild();
        }
    }

    private void refreshOverlayPriorities() {
        for (int i = 0; i < overlays.size(); i++) {
            DynamicChatOverlay overlay = overlays.get(i);
            overlayManager.remove(overlay);
            overlay.setPriority(10f + i);
        }
        for (DynamicChatOverlay overlay : overlays) {
            overlayManager.add(overlay);
        }
    }

    private void addOverlay(OverlayConfig oc) {
        DynamicChatOverlay overlay = new DynamicChatOverlay(this, config, client,
                chatColorConfig, oc);
        overlays.add(overlay);
        overlayManager.add(overlay);
        refreshOverlayPriorities();
    }

    // --- Persistence ---

    private void loadOverlayConfigs() {
        overlayConfigs.clear();
        String json = configManager.getConfiguration(CONFIG_GROUP, OVERLAY_CONFIGS_KEY);
        if (json != null && !json.isEmpty()) {
            try {
                Type listType = new TypeToken<List<OverlayConfig>>() {}.getType();
                List<OverlayConfig> loaded = gson.fromJson(json, listType);
                if (loaded != null && !loaded.isEmpty()) {
                    overlayConfigs.addAll(loaded);
                    firstRun = false;
                    return;
                }
            } catch (Exception e) {
                // Fall through to defaults
            }
        }
        // First run — create defaults
        firstRun = true;
        overlayConfigs.add(OverlayConfig.defaultPrivateWidget());
        overlayConfigs.add(OverlayConfig.defaultAllWidget());
        saveOverlayConfigs();
    }

    public void saveOverlayConfigs() {
        String json = gson.toJson(overlayConfigs);
        configManager.setConfiguration(CONFIG_GROUP, OVERLAY_CONFIGS_KEY, json);
    }

    /**
     * One-time migration of the former global "Show Timestamps" toggle to the per-widget
     * {@link OverlayConfig#isShowTimestamp()} setting. The global {@code @ConfigItem} has been
     * removed, but its previously-persisted value survives in the config store and is read here by
     * key. If it was enabled, every existing widget inherits it so no one loses their timestamps.
     * Guarded by a flag so it never re-applies (e.g. after a user turns a widget's toggle off).
     */
    private void migrateTimestampSetting() {
        if ("true".equals(configManager.getConfiguration(CONFIG_GROUP, TIMESTAMP_MIGRATED_KEY))) {
            return;
        }
        if ("true".equals(configManager.getConfiguration(CONFIG_GROUP, LEGACY_SHOW_TIMESTAMP_KEY))) {
            for (OverlayConfig oc : overlayConfigs) {
                oc.setShowTimestamp(true);
            }
            saveOverlayConfigs();
        }
        configManager.setConfiguration(CONFIG_GROUP, TIMESTAMP_MIGRATED_KEY, true);
    }

    /**
     * One-time migration of the former global "Font Size" setting to the per-widget
     * {@link OverlayConfig#getFontSize()} setting. Reads the orphaned global value (persisted by
     * enum name) directly by key and applies it to every existing widget. Unlike the timestamp
     * migration, this always writes an effective value (defaulting to {@link FontSize#REGULAR}) so
     * widgets deserialized before the field existed are normalized off null. Guarded so it runs once.
     */
    private void migrateFontSizeSetting() {
        if ("true".equals(configManager.getConfiguration(CONFIG_GROUP, FONT_SIZE_MIGRATED_KEY))) {
            return;
        }
        FontSize legacy = FontSize.REGULAR;
        String stored = configManager.getConfiguration(CONFIG_GROUP, LEGACY_FONT_SIZE_KEY);
        if (stored != null && !stored.isEmpty()) {
            try {
                legacy = FontSize.valueOf(stored);
            } catch (IllegalArgumentException ignored) {
                // Unrecognized value — keep the default.
            }
        }
        for (OverlayConfig oc : overlayConfigs) {
            oc.setFontSize(legacy);
        }
        saveOverlayConfigs();
        configManager.setConfiguration(CONFIG_GROUP, FONT_SIZE_MIGRATED_KEY, true);
    }

    /**
     * Arms the one-time "Chat Widgets has been updated" notice when the running {@link #PLUGIN_VERSION}
     * differs from the last version we announced. Suppressed on a brand-new install (nothing to
     * "update" from). The notice is actually posted from {@link #onGameTick} once the player is
     * logged in and the chat is ready.
     */
    private void armUpdateNotice() {
        if (firstRun) {
            configManager.setConfiguration(CONFIG_GROUP, LAST_UPDATE_NOTICE_VERSION_KEY, PLUGIN_VERSION);
            return;
        }
        String lastNoticeVersion = configManager.getConfiguration(CONFIG_GROUP, LAST_UPDATE_NOTICE_VERSION_KEY);
        updateNoticePending = !PLUGIN_VERSION.equals(lastNoticeVersion);
    }

    // --- PM widget visibility ---

    private void updatePmWidgetVisibility() {
        boolean shouldHide = config.hidePrivateChat();
        setPmWidgetsHidden(shouldHide);
        pmWidgetsHidden = shouldHide;
    }

    // --- Event handlers ---

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGGED_IN) {
            updatePmWidgetVisibility();
        }
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event) {
        if (event.getGroupId() == InterfaceID.PM_CHAT && pmWidgetsHidden) {
            setPmWidgetsHidden(true);
        }
    }

    @Subscribe
    public void onVarClientIntChanged(VarClientIntChanged event) {
        if (event.getIndex() == VarClientID.CHAT_VIEW) {
            for (DynamicChatOverlay overlay : overlays) {
                updateSmartPosition(overlay);
            }
        }
    }

    @Subscribe
    public void onResizeableChanged(ResizeableChanged event) {
        for (DynamicChatOverlay overlay : overlays) {
            updateDefaultPosition(overlay);
            updateSmartPosition(overlay);
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        String option = event.getMenuOption();
        if (option == null) {
            return;
        }

        // Handle chatbox tab "Clear history" options
        if (option.contains("Clear")) {
            if (option.contains("Game:")) {
                clearMessagesForCategories(
                        MessageCategory.GAME,
                        MessageCategory.GAME_CLAN);
                return;
            }
            if (option.contains("Public:")) {
                clearMessagesForCategories(MessageCategory.PUBLIC_CHAT, MessageCategory.AUTO);
                return;
            }
            if (option.contains("Private:")) {
                clearMessagesForCategories(MessageCategory.PRIVATE);
                return;
            }
            if (option.contains("Channel:")) {
                clearMessagesForCategories(MessageCategory.FRIENDS_CHAT);
                return;
            }
            if (option.contains("Clan:")) {
                clearMessagesForCategories(
                        MessageCategory.GAME_CLAN,
                        MessageCategory.CLAN_CHAT,
                        MessageCategory.GUEST_CLAN_CHAT,
                        MessageCategory.GIM_CLAN_CHAT);
                return;
            }
        }
    }

    /**
     * Handles clicks on the overlay-level "Clear [name] history" right-click entries. These are
     * {@link net.runelite.client.ui.overlay.OverlayMenuEntry} entries dispatched via
     * {@link OverlayMenuClicked} (not {@code MenuOptionClicked}), so we use the clicked overlay
     * reference directly rather than parsing the menu target string.
     */
    @Subscribe
    public void onOverlayMenuClicked(OverlayMenuClicked event) {
        if (event.getOverlay() instanceof DynamicChatOverlay) {
            DynamicChatOverlay overlay = (DynamicChatOverlay) event.getOverlay();
            clearMessagesForTypes(overlay.getOverlayConfig().getMessageTypes());
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        ChatMessageType type = event.getType();

        if (DEBUG) {
            prependDebugType(event);
        }

        if (!ALL_SUPPORTED_TYPES.contains(type)) {
            return;
        }

        String message = event.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        message = message.trim();

        // Emoji support: for emoji-eligible types that aren't chat commands, seed the stored body
        // from the message node (mutated in place by the RuneLite Emojis plugin to insert <img=N>)
        // rather than the immutable pre-plugin event message. Gated on the Emojis plugin being
        // enabled so we only switch capture source when there's a reason to — otherwise an unrelated
        // plugin's in-place node edit (e.g. Chat Filter censoring) could leak into what we store.
        // Commands (message.startsWith("!")) stay on the command path below. Reconciled on the next
        // tick in onGameTick, catching the case where Emojis is registered after us and hasn't
        // converted yet at capture.
        MessageNode messageNode = event.getMessageNode();
        boolean emojiWatched = EMOJI_WATCHED_TYPES.contains(type)
                && !parsePipes(type, message).getText().startsWith("!")
                && isEmojiPluginEnabled();
        if (emojiWatched && messageNode != null) {
            String nodeValue = messageNode.getValue();
            if (nodeValue != null && !nodeValue.trim().isEmpty()) {
                message = nodeValue.trim();
            }
        }

        // Split off the |-separated payload the chatbox hides (CA_ID:n|, GIM/PvP Arena prefixes,
        // broadcast link keys). Everything below, including Chat Filter, boss KC, merging and
        // duplicate collapse, sees display text. rawMessage stays the node-comparable original.
        String rawMessage = message;
        ChatPipeParser.Result parsed = parsePipes(type, rawMessage);
        message = parsed.getText();
        if (message.trim().isEmpty()) {
            return;
        }

        // Drop messages matching the Chat Filter plugin's lists — only while that plugin is enabled.
        if (config.useChatFilter() && isChatFilterEnabled() && chatMessageFilter.matches(message)) {
            return;
        }

        String sender = cleanSender(event.getName());
        String channelName = cleanSender(event.getSender());
        boolean isOutgoing = type == ChatMessageType.PRIVATECHATOUT;
        boolean isBossKc = BOSS_KC_PATTERN.matcher(message).find();

        // Handle message merging for game-type messages
        if (!SENDER_TYPES.contains(type) && type != ChatMessageType.LOGINLOGOUTNOTIFICATION) {
            for (MessageMergeRule rule : MESSAGE_MERGE_RULES) {
                if (rule.matchesPreviousPrefix(message)) {
                    message = message.replace("<br>", " ");
                    break;
                }
            }

            if (!messages.isEmpty()) {
                WidgetMessage lastMsg = messages.get(messages.size() - 1);
                String merged = tryMergeMessages(lastMsg.getMessage(), message);
                if (merged != null) {
                    int existingCount = 0;
                    if (config.collapseDuplicates()) {
                        String mergedStripped = stripTags(merged);
                        for (int i = messages.size() - 2; i >= 0; i--) {
                            WidgetMessage existing = messages.get(i);
                            if (stripTags(existing.getMessage()).equals(mergedStripped)) {
                                existingCount = existing.getCount();
                                messages.remove(i);
                                break;
                            }
                        }
                    }
                    WidgetMessage mergedMsg = WidgetMessage.gameMessage(
                            merged, System.currentTimeMillis(), lastMsg.getType(), lastMsg.isBossKc());
                    if (existingCount > 0) {
                        mergedMsg.setCount(existingCount + 1);
                    }
                    messages.set(messages.size() - 1, mergedMsg);
                    return;
                }
            }
        }

        // Create the new message
        WidgetMessage newMsg;
        if (type == ChatMessageType.LOGINLOGOUTNOTIFICATION) {
            int maxFade = 5;
            newMsg = WidgetMessage.loginNotification(
                    sender != null ? sender : "System", message, System.currentTimeMillis(), maxFade);
        } else if (SENDER_TYPES.contains(type)) {
            newMsg = WidgetMessage.senderMessage(sender != null ? sender : "Unknown", channelName, message,
                    System.currentTimeMillis(), type, isOutgoing, parsed.getVariant());
        } else {
            newMsg = WidgetMessage.gameMessage(message, System.currentTimeMillis(), type, isBossKc,
                    parsed.getVariant(), parsed.getAchievementTaskId(), parsed.getBroadcastUrl(),
                    clanTagFor(type, parsed.getVariant()));
        }

        // Collapse duplicates (except login notifications). newMsg isn't in the pool yet, so pass
        // skipIndex -1 and base count 1 — the fold adds the matched entry's own count on top.
        if (config.collapseDuplicates() && type != ChatMessageType.LOGINLOGOUTNOTIFICATION) {
            newMsg.setCount(collapseDuplicate(messages, stripTags(message), newMsg.getSender(), 1, -1));
        }

        messages.add(newMsg);

        while (messages.size() > MAX_POOL_SIZE) {
            messages.remove(0);
        }

        // Track potential chat commands for delayed updates by Chat Commands plugin; otherwise
        // watch emoji-eligible messages for the Emojis plugin's next-tick <img=N> conversion.
        // originalText is the raw (pre-split) text, since that is what the node accessors return.
        if (messageNode != null && message.startsWith("!")) {
            pendingUpdates.add(new PendingMessageUpdate(
                    newMsg, messageNode, COMMAND_VALUE, rawMessage, COMMAND_UPDATE_TICKS));
        } else if (emojiWatched && messageNode != null) {
            pendingUpdates.add(new PendingMessageUpdate(
                    newMsg, messageNode, EMOJI_VALUE, rawMessage, EMOJI_UPDATE_TICKS));
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (updateNoticePending && client.getGameState() == GameState.LOGGED_IN) {
            updateNoticePending = false;
            client.addChatMessage(ChatMessageType.CONSOLE, "",
                    "<col=00b000>Chat Widgets updated to v" + PLUGIN_VERSION
                            + " — new per-widget Font Size, Timestamps & Input Preview; Game and Clan are now separate message types. See the side panel!</col>", null);
            configManager.setConfiguration(CONFIG_GROUP, LAST_UPDATE_NOTICE_VERSION_KEY, PLUGIN_VERSION);
        }

        reconcilePendingUpdates();
    }

    /**
     * Drains {@link #pendingUpdates}, rebuilding any pooled message whose backing node has been
     * rewritten since capture (Chat Commands results, Emojis {@code <img=N>} tags). Each entry is
     * dropped once its value changes and the message is rebuilt, or once its tick budget runs out.
     */
    private void reconcilePendingUpdates() {
        for (int i = pendingUpdates.size() - 1; i >= 0; i--) {
            PendingMessageUpdate pending = pendingUpdates.get(i);
            pending.ticksRemaining--;

            String currentValue = pending.valueAccessor.apply(pending.messageNode);
            if (currentValue != null && !currentValue.equals(pending.originalText)) {
                rebuildPooledMessage(pending.widgetMessage, currentValue);
                pendingUpdates.remove(i);
            } else if (pending.ticksRemaining <= 0) {
                pendingUpdates.remove(i);
            }
        }
    }

    /**
     * Replaces the pooled {@code old} message with a copy carrying {@code newBody}, preserving its
     * kind, count, and metadata. The node value is raw, so it is split again first; otherwise a
     * rewrite would bring a hidden {@code |} prefix back. Re-applies duplicate collapsing afterwards:
     * the body only reaches its final form here (a command result, or an Emojis {@code <img=N>} tag),
     * so a duplicate the capture-time text couldn't match may only surface post-rewrite. No-op if
     * {@code old} has already been evicted from the pool; a rewrite that is blank once split drops
     * the message, as at capture.
     */
    private void rebuildPooledMessage(WidgetMessage old, String newBody) {
        int idx = messages.indexOf(old);
        if (idx < 0) {
            return;
        }
        // GIM membership snapshotted at capture (via the variant), so text and variant stay in step.
        boolean inGim = old.getVariant() == MessageVariant.GIM;
        String displayBody = ChatPipeParser.parse(old.getType(), newBody, inGim, this::lookupBroadcastUrl).getText();
        if (displayBody.trim().isEmpty()) {
            messages.remove(idx);
            return;
        }
        WidgetMessage updated = old.withMessage(displayBody);
        if (old.getCount() > 1) {
            updated.setCount(old.getCount());
        }
        messages.set(idx, updated);

        // The shortcut-vs-<img> (or command-vs-result) mismatch at capture time can hide a
        // duplicate that only matches once the body reaches its final form here. updated is already
        // in the pool, so skip its own slot and fold on top of the count it already carries.
        if (config.collapseDuplicates() && updated.getType() != ChatMessageType.LOGINLOGOUTNOTIFICATION) {
            updated.setCount(collapseDuplicate(
                    messages, stripTags(displayBody), updated.getSender(), updated.getCount(), idx));
        }
    }

    /** Runs {@link ChatPipeParser} with this client's GIM membership and broadcast URL enum. */
    private ChatPipeParser.Result parsePipes(ChatMessageType type, String raw) {
        boolean inGim = type == ChatMessageType.CLAN_CHAT && isInGim();
        return ChatPipeParser.parse(type, raw, inGim, this::lookupBroadcastUrl);
    }

    /** Mirrors cs2's GIM check for clan chat: GIM clan settings and channel both present. */
    private boolean isInGim() {
        return client.getClanSettings(ClanID.GROUP_IRONMAN) != null
                && client.getClanChannel(ClanID.GROUP_IRONMAN) != null;
    }

    private String lookupBroadcastUrl(int key) {
        EnumComposition urls = client.getEnum(BROADCAST_URL_ENUM);
        return urls == null ? null : urls.getStringValue(key);
    }

    /**
     * Clan name to tag a clan notification with, snapshotted from live clan state the way cs2
     * picks it: main clan channel name, GIM clan settings name, or {@code PvP Arena} while in that
     * channel. {@code null} for other types or when the player is not in the relevant clan.
     */
    private String clanTagFor(ChatMessageType type, MessageVariant variant) {
        if (type != ChatMessageType.CLAN_MESSAGE && type != ChatMessageType.CLAN_GIM_MESSAGE) {
            return null;
        }
        switch (variant) {
            case GIM:
                ClanSettings gim = client.getClanSettings(ClanID.GROUP_IRONMAN);
                return gim == null ? null : gim.getName();
            case PVP_ARENA:
                return client.getClanChannel(PVP_ARENA_CLAN_ID) == null ? null : PVP_ARENA_TAG;
            default:
                ClanChannel clan = client.getClanChannel(ClanID.CLAN);
                return clan == null ? null : clan.getName();
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        // Keep our copy of the Chat Filter lists in sync as the user edits them.
        if ("chatfilter".equals(event.getGroup())) {
            rebuildChatFilter();
            return;
        }
        if (!event.getGroup().equals(CONFIG_GROUP)) {
            return;
        }
        if ("hideSidePanel".equals(event.getKey())) {
            if (config.hideSidePanel()) {
                clientToolbar.removeNavigation(navButton);
            } else {
                clientToolbar.addNavigation(navButton);
            }
        }
        if ("hidePrivateChat".equals(event.getKey())) {
            updatePmWidgetVisibility();
        }
    }

    /** Recompiles the Chat Filter patterns from the Chat Filter plugin's live config. */
    private void rebuildChatFilter() {
        chatMessageFilter.rebuild(configManager.getConfig(ChatFilterConfig.class));
    }

    /**
     * True when RuneLite's built-in Chat Filter plugin is currently enabled. The config lists are
     * always readable, but we only filter when the plugin itself is on — otherwise the user isn't
     * filtering their chat and shouldn't have widget messages silently removed.
     */
    private boolean isChatFilterEnabled() {
        if (chatFilterPlugin == null) {
            for (Plugin p : pluginManager.getPlugins()) {
                if (p instanceof ChatFilterPlugin) {
                    chatFilterPlugin = p;
                    break;
                }
            }
        }
        return chatFilterPlugin != null && pluginManager.isPluginEnabled(chatFilterPlugin);
    }

    /**
     * True when RuneLite's Emojis plugin is currently enabled. Only then do we seed captured bodies
     * from the live message node (and watch for its {@code <img=N>} rewrite); otherwise we keep the
     * raw event message so an unrelated plugin's in-place node edit (e.g. Chat Filter censoring)
     * can't silently change what we store and render.
     */
    private boolean isEmojiPluginEnabled() {
        if (emojiPlugin == null) {
            for (Plugin p : pluginManager.getPlugins()) {
                if (p instanceof EmojiPlugin) {
                    emojiPlugin = p;
                    break;
                }
            }
        }
        return emojiPlugin != null && pluginManager.isPluginEnabled(emojiPlugin);
    }

    // --- Message access for overlays ---

    public List<WidgetMessage> getMessagesForOverlay(OverlayConfig overlayConfig) {
        Set<ChatMessageType> types = overlayConfig.getMessageTypes();
        int size = messages.size();
        if (size == 0 || types.isEmpty()) {
            return new ArrayList<>(0);
        }

        long currentTime = System.currentTimeMillis();
        int fadeOutDuration = overlayConfig.getFadeOutDuration();
        long fadeOutThreshold = fadeOutDuration > 0 ? (fadeOutDuration * 2000L) + 2000 : 0;
        boolean gameFilterEnabled = isGameFilterEnabled();
        boolean bossKcFilterEnabled = isBossKcFilterEnabled();

        int maxMessages = overlayConfig.getMaxMessages();
        List<WidgetMessage> filtered = new ArrayList<>(maxMessages);
        int msgCount = 0;

        for (int i = size - 1; i >= 0; i--) {
            WidgetMessage msg = messages.get(i);

            if (!types.contains(msg.getType())) {
                continue;
            }

            if (fadeOutThreshold > 0) {
                int msgMaxFade = msg.getMaxFadeSeconds();
                long threshold = msgMaxFade > 0 ? (msgMaxFade * 1000L) + 2000 : fadeOutThreshold;
                if (currentTime - msg.getTimestamp() >= threshold) {
                    continue;
                }
            }

            if (gameFilterEnabled && msg.getType() == ChatMessageType.SPAM) {
                continue;
            }

            if (bossKcFilterEnabled && msg.isBossKc()) {
                continue;
            }

            // Login notifications don't count against max
            boolean isLoginNotification = msg.getType() == ChatMessageType.LOGINLOGOUTNOTIFICATION;
            if (!isLoginNotification && msgCount >= maxMessages) {
                continue;
            }

            filtered.add(0, msg);
            if (!isLoginNotification) {
                msgCount++;
            }
        }

        return filtered;
    }

    public void clearMessagesForTypes(Set<ChatMessageType> types) {
        messages.removeIf(msg -> types.contains(msg.getType()));
    }

    public void clearAllMessages() {
        messages.clear();
    }

    // --- Utility ---

    private String cleanSender(String name) {
        if (name == null) {
            return null;
        }
        return name.replace('\u00A0', ' ').trim();
    }

    private String tryMergeMessages(String previousMessage, String newMessage) {
        for (MessageMergeRule rule : MESSAGE_MERGE_RULES) {
            if (rule.matches(previousMessage, newMessage)) {
                return rule.merge(previousMessage, newMessage);
            }
        }
        return null;
    }

    private static String stripTags(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("</?col[^>]*>", "");
    }

    /**
     * Folds a duplicate message into a target's slot: scans {@code pool} for an entry with the same
     * colour-stripped body and sender, and on a hit removes it and returns the combined count.
     *
     * @param pool         the shared message pool, mutated in place (the matched entry is removed)
     * @param strippedBody the target's body with colour tags stripped ({@link #stripTags})
     * @param sender       the target's sender ({@code null} for game messages)
     * @param baseCount    the count the target carries before folding — 1 for a freshly captured
     *                     message, or its preserved count when rebuilt after a node rewrite
     * @param skipIndex    the target's own index when it already lives in {@code pool} (so it isn't
     *                     matched against itself), or -1 when it has not been added yet
     * @return {@code baseCount} plus the matched entry's count, or {@code baseCount} unchanged when
     *         no duplicate is found (pool left untouched)
     */
    static int collapseDuplicate(List<WidgetMessage> pool, String strippedBody, String sender,
            int baseCount, int skipIndex) {
        for (int i = pool.size() - 1; i >= 0; i--) {
            if (i == skipIndex) {
                continue;
            }
            WidgetMessage other = pool.get(i);
            String otherSender = other.getSender();
            if (stripTags(other.getMessage()).equals(strippedBody)
                    && (otherSender == null ? sender == null : otherSender.equals(sender))) {
                pool.remove(i);
                return baseCount + other.getCount();
            }
        }
        return baseCount;
    }

    /**
     * DEBUG aid: prepends the message's {@link ChatMessageType} to the live chatbox line so the
     * type of every message (including ones the plugin doesn't capture) is visible in-game. Only
     * the chatbox node is modified; the widget pool reads {@code event.getMessage()}, a separate
     * copy, so widgets still render the clean message.
     */
    private void prependDebugType(ChatMessage event) {
        MessageNode node = event.getMessageNode();
        if (node == null) {
            return;
        }
        node.setValue("<col=ff0000>[" + event.getType().name() + "]</col> " + node.getValue());
    }

    private void clearMessagesForCategories(MessageCategory... categories) {
        EnumSet<ChatMessageType> types = EnumSet.noneOf(ChatMessageType.class);
        for (MessageCategory cat : categories) {
            types.addAll(cat.getTypes());
        }
        clearMessagesForTypes(types);
    }

    public boolean isChatboxHidden() {
        return isChatboxMinimized() || isChatboxWidgetHidden();
    }

    private boolean isChatboxMinimized() {
        return client.getVarcIntValue(VarClientID.CHAT_VIEW) == 1337;
    }

    private boolean isChatboxWidgetHidden() {
        Widget chatboxWidget = client.getWidget(InterfaceID.Chatbox.CHATAREA);
        return (chatboxWidget != null && chatboxWidget.isHidden());
    }

    public boolean isGameFilterEnabled() {
        return client.getVarbitValue(VarbitID.GAME_FILTER) == 1;
    }

    public boolean isBossKcFilterEnabled() {
        return client.getVarbitValue(VarbitID.BOSS_KILLCOUNT_FILTERED) == 1;
    }

    private void updateDefaultPosition(net.runelite.client.ui.overlay.Overlay overlay) {
        OverlayPosition defaultPos = client.isResized()
                ? OverlayPosition.ABOVE_CHATBOX_RIGHT
                : OverlayPosition.BOTTOM_LEFT;
        overlay.setPosition(defaultPos);
    }

    private void updateSmartPosition(net.runelite.client.ui.overlay.Overlay overlay) {
        if (!config.smartPositioning()) {
            return;
        }

        OverlayPosition currentPos = overlay.getPreferredPosition() != null
                ? overlay.getPreferredPosition()
                : overlay.getPosition();

        if (isTopPosition(currentPos)) {
            return;
        }

        OverlayPosition targetPos;
        if (!client.isResized()) {
            targetPos = OverlayPosition.BOTTOM_LEFT;
        } else if (isChatboxHidden()) {
            targetPos = OverlayPosition.ABOVE_CHATBOX_RIGHT;
        } else {
            return;
        }

        if (currentPos != targetPos) {
            overlay.setPreferredPosition(targetPos);
        }
    }

    private boolean isTopPosition(OverlayPosition position) {
        return position == OverlayPosition.TOP_CENTER
                || position == OverlayPosition.TOP_RIGHT
                || position == OverlayPosition.TOP_LEFT
                || position == OverlayPosition.CANVAS_TOP_RIGHT;
    }

    private void setPmWidgetsHidden(boolean hidden) {
        setGameframePmContainerHidden(InterfaceID.TOPLEVEL, 36, hidden);
        setGameframePmContainerHidden(InterfaceID.TOPLEVEL_OSRS_STRETCH, 93, hidden);
        setGameframePmContainerHidden(InterfaceID.TOPLEVEL_PRE_EOC, 90, hidden);

        Widget pmContainer = client.getWidget(InterfaceID.PM_CHAT, 0);
        if (pmContainer != null) {
            pmContainer.setHidden(hidden);
            Widget[] dynamicChildren = pmContainer.getDynamicChildren();
            if (dynamicChildren != null) {
                for (Widget child : dynamicChildren) {
                    if (child != null) {
                        child.setHidden(hidden);
                    }
                }
            }
        }
    }

    private void setGameframePmContainerHidden(int group, int child, boolean hidden) {
        Widget container = client.getWidget(group, child);
        if (container != null) {
            container.setHidden(hidden);
        }
    }

    @Provides
    ChatWidgetConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ChatWidgetConfig.class);
    }

    boolean isHoveringGameChatControls() {
        final Point mouse = client.getMouseCanvasPosition();
        return isHoveringWidget(mouse, client.getWidget(InterfaceID.Chatbox.CHAT_ALL)) ||
                isHoveringWidget(mouse, client.getWidget(InterfaceID.Chatbox.CHAT_GAME)) ||
                isHoveringWidget(mouse, client.getWidget(InterfaceID.Chatbox.CHAT_PUBLIC));
    }

    boolean isHoveringWidget(Point mouse, Widget target) {
        if (target == null || target.isHidden()) {
            return false;
        }
        return target.getBounds().contains(mouse.getX(), mouse.getY());
    }
}
