package com.chatwidgets;

import com.chatwidgets.model.FontSize;
import com.chatwidgets.model.MessageCategory;
import com.chatwidgets.model.WidgetPosition;
import com.chatwidgets.overlay.OverlayConfig;
import net.runelite.api.ChatMessageType;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.colorpicker.RuneliteColorPicker;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * RuneLite sidebar panel for managing overlay widgets. Users can add, delete, reorder,
 * and configure overlays here.
 */
public class ChatWidgetPanel extends PluginPanel {

    private final ChatWidgetPlugin plugin;
    private final Map<String, Boolean> collapsedState = new HashMap<>();
    private JComponent debugSection;
    private boolean debugCollapsed = true;

    public ChatWidgetPanel(ChatWidgetPlugin plugin) {
        super(false);
        this.plugin = plugin;
        rebuild();
    }

    /** Shows a dev-only Debug section below the widgets, or removes it when null. */
    public void setDebugSection(JComponent section) {
        debugSection = section;
        rebuild();
    }

    public void rebuild() {
        removeAll();
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Header with add button
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        headerPanel.setBorder(new EmptyBorder(8, 4, 4, 4));
        headerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = new JLabel("Chat Widgets");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        headerPanel.add(title, BorderLayout.WEST);

        JPanel headerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        headerButtons.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JButton supportButton = new JButton();
        BufferedImage supportIcon = ImageUtil.loadImageResource(ChatWidgetPanel.class, "/support.png");
        if (supportIcon != null) {
            supportButton.setIcon(new ImageIcon(supportIcon));
        }
        supportButton.setFocusPainted(false);
        supportButton.setToolTipText("Support");
        supportButton.setMargin(new Insets(1, 6, 1, 6));
        supportButton.addActionListener(e -> LinkBrowser.browse("https://buymeacoffee.com/cnnnr"));

        JButton addButton = new JButton("+");
        addButton.setToolTipText("Add new widget");
        addButton.setFocusPainted(false);
        addButton.addActionListener(e -> plugin.addNewOverlay());
        headerButtons.add(addButton);

        JButton resetButton = new JButton("⟳");
        resetButton.setFocusPainted(false);
        resetButton.setToolTipText("Reset to the default widgets");
        resetButton.setMargin(new Insets(1, 6, 1, 6));
        resetButton.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(this,
                    "Delete all chat widgets and restore the default 2?",
                    "Reset Chat Widgets", JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                plugin.resetOverlays();
            }
        });
        headerButtons.add(resetButton);
        headerButtons.add(supportButton);

        headerPanel.add(headerButtons, BorderLayout.EAST);

        contentPanel.add(headerPanel);

        // Overlay sections
        for (OverlayConfig oc : plugin.getOverlayConfigs()) {
            contentPanel.add(buildOverlaySection(oc));
        }

        if (debugSection != null) {
            contentPanel.add(buildDebugSection());
        }

        // Wrap in a top-aligned container so items don't stretch vertically
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        wrapper.add(contentPanel, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(wrapper);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        revalidate();
        repaint();
    }

    private JPanel buildOverlaySection(OverlayConfig oc) {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
                new EmptyBorder(4, 4, 4, 4)
        ));

        boolean collapsed = collapsedState.getOrDefault(oc.getId(), true);

        // Collapsible header row
        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        headerRow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel arrow = new JLabel(collapsed ? "⮞ " : "⮟ ");
        arrow.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        JLabel nameLabel = new JLabel(oc.getName());
        nameLabel.setForeground(oc.isShow() ? Color.WHITE : new Color(255, 255, 255, 100));
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));

        JPanel namePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        namePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        namePanel.add(arrow);
        namePanel.add(nameLabel);
        headerRow.add(namePanel, BorderLayout.CENTER);

        JPanel orderButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        orderButtons.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JLabel upBtn = new JLabel("\uD83E\uDC25");
        upBtn.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        upBtn.setBorder(new EmptyBorder(0, 3, 0, 3));
        upBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        upBtn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                plugin.moveOverlay(oc, -1);
            }
        });

        JLabel downBtn = new JLabel("\uD83E\uDC27");
        downBtn.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        downBtn.setBorder(new EmptyBorder(0, 3, 0, 3));
        downBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        downBtn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                plugin.moveOverlay(oc, 1);
            }
        });

        orderButtons.add(upBtn);
        orderButtons.add(downBtn);
        headerRow.add(orderButtons, BorderLayout.EAST);

        // Content panel (everything below the header)
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.setVisible(!collapsed);

        headerRow.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                boolean nowCollapsed = !collapsedState.getOrDefault(oc.getId(), false);
                collapsedState.put(oc.getId(), nowCollapsed);
                content.setVisible(!nowCollapsed);
                arrow.setText(nowCollapsed ? "⮞ " : "⮟ ");
                section.revalidate();
                section.repaint();
            }
        });

        section.add(headerRow);
        content.add(createStrut(4));

        // Title (editable name)
        content.add(buildTitleRow(oc, nameLabel));

        // Show toggle
        content.add(buildCheckbox("Show", oc.isShow(), v -> {
            oc.setShow(v);
            nameLabel.setForeground(v ? Color.WHITE : new Color(255, 255, 255, 100));
            plugin.onOverlayConfigChanged();
        }));

        // Message types - inline row like other settings
        content.add(buildMessageTypeRow(oc));

        // Settings rows
        content.add(buildCheckbox("Always Visible", oc.isAlwaysVisible(), v -> {
            oc.setAlwaysVisible(v);
            plugin.onOverlayConfigChanged();
        }));
        content.add(buildCheckbox("Contextual Colours", oc.isContextualColours(), v -> {
            oc.setContextualColours(v);
            plugin.onOverlayConfigChanged();
        }));
        content.add(buildColorRow("Background", oc::getBackgroundColour, oc::setBackgroundColour));
        content.add(buildCheckbox("Show Timestamps", oc.isShowTimestamp(), v -> {
            oc.setShowTimestamp(v);
            plugin.onOverlayConfigChanged();
        }));
        content.add(buildCheckbox("Show Input Preview", oc.isShowInputPreview(), v -> {
            oc.setShowInputPreview(v);
            plugin.onOverlayConfigChanged();
        }));
        content.add(buildCheckbox("Preview Only While Typing", oc.isPreviewOnlyWhenTyping(), v -> {
            oc.setPreviewOnlyWhenTyping(v);
            plugin.onOverlayConfigChanged();
        }));
        content.add(buildComboRow("Font Size", FontSize.values(), oc.getFontSize(), v -> {
            oc.setFontSize(v);
            plugin.onOverlayConfigChanged();
        }));
        content.add(buildSpinnerRow("Max Messages", oc.getMaxMessages(), 1, 20, v -> {
            oc.setMaxMessages(v);
            plugin.onOverlayConfigChanged();
        }));
        content.add(buildSpinnerRow("Fade Out (sec)", oc.getFadeOutDuration(), 0, 300, v -> {
            oc.setFadeOutDuration(v);
            plugin.onOverlayConfigChanged();
        }));
        content.add(buildComboRow("Position", WidgetPosition.values(), oc.getPosition(), v -> {
            oc.setPosition(v);
            plugin.onOverlayConfigChanged();
        }));
        content.add(buildCheckbox("Dynamic Height", oc.isDynamicHeight(), v -> {
            oc.setDynamicHeight(v);
            plugin.onOverlayConfigChanged();
        }));
        content.add(buildSpinnerRow("Margin Top", oc.getMarginTop(), 0, 200, v -> {
            oc.setMarginTop(v);
            plugin.onOverlayConfigChanged();
        }));
        content.add(buildSpinnerRow("Margin Bottom", oc.getMarginBottom(), 0, 200, v -> {
            oc.setMarginBottom(v);
            plugin.onOverlayConfigChanged();
        }));
        content.add(buildSpinnerRow("Margin Left", oc.getMarginLeft(), 0, 200, v -> {
            oc.setMarginLeft(v);
            plugin.onOverlayConfigChanged();
        }));
        content.add(buildSpinnerRow("Margin Right", oc.getMarginRight(), 0, 200, v -> {
            oc.setMarginRight(v);
            plugin.onOverlayConfigChanged();
        }));

        // Delete button
        content.add(createStrut(6));
        JButton deleteButton = new JButton("Delete");
        deleteButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        deleteButton.setForeground(new Color(255, 80, 80));
        deleteButton.setFocusPainted(false);
        deleteButton.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(this,
                    "Delete chat widget \"" + oc.getName() + "\"?",
                    "Confirm", JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                plugin.removeOverlay(oc);
            }
        });
        content.add(deleteButton);

        section.add(content);
        return section;
    }

    private JPanel buildDebugSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setBorder(new CompoundBorder(
                new MatteBorder(8, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
                new EmptyBorder(4, 4, 4, 4)
        ));

        JPanel headerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        headerRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        headerRow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel arrow = new JLabel(debugCollapsed ? "⮞ " : "⮟ ");
        arrow.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        JLabel nameLabel = new JLabel("Debug");
        nameLabel.setForeground(ColorScheme.BRAND_ORANGE);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
        headerRow.add(arrow);
        headerRow.add(nameLabel);

        debugSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        debugSection.setVisible(!debugCollapsed);

        headerRow.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                debugCollapsed = !debugCollapsed;
                debugSection.setVisible(!debugCollapsed);
                arrow.setText(debugCollapsed ? "⮞ " : "⮟ ");
                section.revalidate();
                section.repaint();
            }
        });

        section.add(headerRow);
        section.add(createStrut(4));
        section.add(debugSection);
        return section;
    }

    private JPanel buildMessageTypeRow(OverlayConfig oc) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lbl = new JLabel("Message Types");
        lbl.setForeground(Color.WHITE);
        row.add(lbl, BorderLayout.CENTER);

        JButton selectButton = new JButton(getMessageTypeSummary(oc));
        selectButton.setFocusPainted(false);
        selectButton.setPreferredSize(new Dimension(90, 24));
        selectButton.setHorizontalAlignment(SwingConstants.LEFT);
        selectButton.setMargin(new Insets(1, 4, 1, 4));

        selectButton.addActionListener(e -> {
            JPopupMenu popup = new JPopupMenu();

            for (MessageCategory category : MessageCategory.values()) {
                Set<ChatMessageType> selected = oc.getMessageTypes();
                boolean allSelected = selected.containsAll(category.getTypes());

                JCheckBoxMenuItem item = new JCheckBoxMenuItem(category.getDisplayName(), allSelected);
                item.addActionListener(ev -> {
                    Set<ChatMessageType> current = oc.getMessageTypes();
                    EnumSet<ChatMessageType> updated = current.isEmpty()
                            ? EnumSet.noneOf(ChatMessageType.class)
                            : EnumSet.copyOf(current);

                    if (item.isSelected()) {
                        updated.addAll(category.getTypes());
                    } else {
                        category.getTypes().forEach(updated::remove);
                    }

                    oc.setMessageTypes(updated);
                    plugin.onOverlayConfigChanged();
                    selectButton.setText(getMessageTypeSummary(oc));
                });
                popup.add(item);
            }

            popup.show(selectButton, 0, selectButton.getHeight());
        });

        row.add(selectButton, BorderLayout.EAST);
        return row;
    }

    private String getMessageTypeSummary(OverlayConfig oc) {
        Set<ChatMessageType> selected = oc.getMessageTypes();
        if (selected.isEmpty()) {
            return "None";
        }

        int count = 0;
        String firstName = null;
        for (MessageCategory category : MessageCategory.values()) {
            if (selected.containsAll(category.getTypes())) {
                if (firstName == null) {
                    firstName = category.getDisplayName();
                }
                count++;
            }
        }

        if (count == 0) {
            return "Custom";
        }
        if (count == 1) {
            return firstName;
        }
        return count + " types";
    }

    // --- UI helpers ---

    private JPanel buildTitleRow(OverlayConfig oc, JLabel headerNameLabel) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lbl = new JLabel("Title");
        lbl.setForeground(Color.WHITE);
        row.add(lbl, BorderLayout.CENTER);

        JTextField field = new JTextField(oc.getName());
        field.setPreferredSize(new Dimension(110, 24));
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                String newName = field.getText().trim();
                if (!newName.isEmpty()) {
                    oc.setName(newName);
                    headerNameLabel.setText(newName);
                    plugin.onOverlayConfigChanged();
                }
            }
        });
        field.addActionListener(e -> field.transferFocus());
        row.add(field, BorderLayout.EAST);

        return row;
    }

    private Component createStrut(int height) {
        Component strut = Box.createVerticalStrut(height);
        ((JComponent) strut).setAlignmentX(Component.LEFT_ALIGNMENT);
        return strut;
    }

    private JPanel buildColorRow(String label, java.util.function.Supplier<Color> getter,
            java.util.function.Consumer<Color> setter) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lbl = new JLabel(label);
        lbl.setForeground(Color.WHITE);
        row.add(lbl, BorderLayout.CENTER);

        JButton swatch = new JButton();
        swatch.setPreferredSize(new Dimension(90, 24));
        swatch.setFocusPainted(false);
        swatch.setBackground(getter.get());
        swatch.addActionListener(e -> {
            RuneliteColorPicker picker = plugin.getColorPickerManager().create(
                    SwingUtilities.getWindowAncestor(this), getter.get(), label, false);
            picker.setLocationRelativeTo(this);
            picker.setOnColorChange(c -> {
                setter.accept(c);
                swatch.setBackground(c);
            });
            picker.setOnClose(c -> plugin.onOverlayConfigChanged());
            picker.setVisible(true);
        });
        row.add(swatch, BorderLayout.EAST);

        return row;
    }

    private JPanel buildCheckbox(String label, boolean value, java.util.function.Consumer<Boolean> onChange) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lbl = new JLabel(label);
        lbl.setForeground(Color.WHITE);
        row.add(lbl, BorderLayout.CENTER);

        JCheckBox cb = new JCheckBox();
        cb.setSelected(value);
        cb.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        cb.setFocusPainted(false);
        cb.addActionListener(e -> onChange.accept(cb.isSelected()));
        row.add(cb, BorderLayout.EAST);

        return row;
    }

    private JPanel buildSpinnerRow(String label, int value, int min, int max,
            java.util.function.Consumer<Integer> onChange) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lbl = new JLabel(label);
        lbl.setForeground(Color.WHITE);
        row.add(lbl, BorderLayout.CENTER);

        SpinnerNumberModel model = new SpinnerNumberModel(value, min, max, 1);
        JSpinner spinner = new JSpinner(model);
        spinner.setPreferredSize(new Dimension(60, 24));
        spinner.addChangeListener(e -> onChange.accept((Integer) spinner.getValue()));
        row.add(spinner, BorderLayout.EAST);

        return row;
    }

    private <T> JPanel buildComboRow(String label, T[] values, T selected,
            java.util.function.Consumer<T> onChange) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lbl = new JLabel(label);
        lbl.setForeground(Color.WHITE);
        row.add(lbl, BorderLayout.CENTER);

        JComboBox<T> combo = new JComboBox<>(values);
        combo.setSelectedItem(selected);
        combo.setPreferredSize(new Dimension(90, 24));
        combo.addActionListener(e -> {
            @SuppressWarnings("unchecked")
            T val = (T) combo.getSelectedItem();
            onChange.accept(val);
        });
        row.add(combo, BorderLayout.EAST);

        return row;
    }
}
