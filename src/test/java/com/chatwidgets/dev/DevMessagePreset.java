package com.chatwidgets.dev;

import net.runelite.api.ChatMessageType;

/**
 * Canned messages that reproduce formatting issues which are hard to trigger in-game. Text is the
 * raw string as it arrives in {@code ChatMessage.getMessage()}, before any client expansion.
 */
public enum DevMessagePreset {
    ALL("All presets", null, "", "", ""),

    // #40: font tags other than col/img/br
    PVP_TRACKER_TAGS("Shadow, underline, <html> (#40)", ChatMessageType.GAMEMESSAGE, "", "",
            "<html><shad=000000>PvP Performance Tracker <u>v.1.9.0</u> Update:</shad> Added Pete Kayer"
                    + " fight tracking in his arena for both his Penultimate and Ultimate fights."),
    STRIKE_TAGS("Strikethrough (#40)", ChatMessageType.GAMEMESSAGE, "", "",
            "<str>default strike</str> <str=00ff00>green strike</str> <u=ff0000>red underline</u> plain"),
    BR_RESETS_STATE("<br> mid-underline (#40)", ChatMessageType.GAMEMESSAGE, "", "",
            "<u><col=ff0000>red underlined<br>after break: base colour, no underline"),
    BAD_HEX("Bad hex colour (#40)", ChatMessageType.GAMEMESSAGE, "", "",
            "<col=zz>bad hex is ignored</col> <col=ff0000>red</col> back to base"),
    ESCAPES("Escape tags (#40)", ChatMessageType.GAMEMESSAGE, "", "",
            "lt/gt: <lt>tag<gt> at: a<at>b nbh: non<nbh>breaking n: line one<n>line two"),

    // #40: template variables
    CLAN_QUEST_TEMPLATE("Clan quest template (#40)", ChatMessageType.CLAN_MESSAGE, "", "Test Clan",
            "<str_quest_name_0=Crab Quest>Username has completed a quest: <str_quest_name_0>"),
    TEMPLATE_NOT_STRIKE("Template is not <str> (#40)", ChatMessageType.GAMEMESSAGE, "", "",
            "<str_x=value>should not be struck through: <str_x>"),

    // #28: pipe prefix
    CLAN_CA_ID("Clan CA_ID prefix (#28)", ChatMessageType.CLAN_MESSAGE, "", "Test Clan",
            "CA_ID:565|Username has completed a grandmaster combat task: Swimming in Venom."),
    GAME_CA_ID("Game CA_ID prefix (#28)", ChatMessageType.GAMEMESSAGE, "", "",
            "CA_ID:565|Congratulations, you've completed a grandmaster combat task: Swimming in Venom."),

    // #28 / #37 / #29: colour macros
    MACRO_RED("@mes_hl_red@ macro (#28, #37)", ChatMessageType.GAMEMESSAGE, "", "",
            "@mes_hl_red@Your potion effect has run out.</col>"),
    MACRO_GREEN("@mes_hl_gre@ macro (#28)", ChatMessageType.GAMEMESSAGE, "", "",
            "@mes_hl_gre@Your stamina has been restored.</col>"),
    PLAYER_AT_SIGNS("Player chat @ (#29)", ChatMessageType.PUBLICCHAT, "Tester", "",
            "escaped: <at>mes_hl_red<at> raw: @mes_hl_red@ should stay literal");

    private final String label;
    final ChatMessageType type;
    final String playerName;
    final String sender;
    final String message;

    DevMessagePreset(String label, ChatMessageType type, String playerName, String sender, String message) {
        this.label = label;
        this.type = type;
        this.playerName = playerName;
        this.sender = sender;
        this.message = message;
    }

    @Override
    public String toString() {
        return label;
    }
}
