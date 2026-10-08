package com.chatwidgets.dev;

import net.runelite.api.ChatMessageType;
import net.runelite.client.ui.ColorScheme;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;

/**
 * Debug section content for {@link DevMessagesPlugin}, shown inside the Chat Widgets sidebar.
 * Picking a preset copies its type, name, sender and text into the fields below so they can be
 * edited before sending. A plain panel rather than a {@code Config}, because RuneLite's config
 * panel does not redraw when a value is set from code.
 */
class DevMessagesPanel extends JPanel {

    /** Receives a message to send; called on the Swing thread. */
    interface Sender {
        void send(ChatMessageType type, String name, String message, String sender);
    }

    private final JComboBox<DevMessagePreset> presetBox = new JComboBox<>(DevMessagePreset.values());
    private final JComboBox<ChatMessageType> typeBox = new JComboBox<>(ChatMessageType.values());
    private final JTextField nameField = new JTextField();
    private final JTextField senderField = new JTextField();
    private final JTextArea messageArea = new JTextArea(8, 20);

    DevMessagesPanel(Sender sender) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(ColorScheme.DARKER_GRAY_COLOR);

        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);

        presetBox.addActionListener(e -> loadPreset((DevMessagePreset) presetBox.getSelectedItem()));

        JButton sendButton = new JButton("Send");
        sendButton.addActionListener(e -> sender.send((ChatMessageType) typeBox.getSelectedItem(),
                nameField.getText(), messageArea.getText(), senderField.getText()));

        JButton sendAllButton = new JButton("Send all presets");
        sendAllButton.addActionListener(e -> {
            for (DevMessagePreset p : DevMessagePreset.values()) {
                sender.send(p.type, p.playerName, p.message, p.sender);
            }
        });

        add(labelled("Preset", presetBox));
        add(labelled("Type", typeBox));
        add(labelled("Name (player chat)", nameField));
        add(labelled("Sender (channel name)", senderField));
        // RuneLite's LAF gives scroll panes an empty border, so outline the box against the section
        JScrollPane messageScroll = new JScrollPane(messageArea);
        messageScroll.setBorder(new LineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
        add(labelled("Message", messageScroll));

        JPanel buttons = new JPanel(new GridLayout(2, 1, 0, 4));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.add(sendButton);
        buttons.add(sendAllButton);
        fillWidth(buttons);
        add(buttons);

        loadPreset((DevMessagePreset) presetBox.getSelectedItem());
    }

    private void loadPreset(DevMessagePreset preset) {
        if (preset == null) {
            return;
        }
        typeBox.setSelectedItem(preset.type);
        nameField.setText(preset.playerName);
        senderField.setText(preset.sender);
        messageArea.setText(preset.message);
        messageArea.setCaretPosition(0);
    }

    private static JPanel labelled(String label, Component field) {
        JPanel row = new JPanel(new BorderLayout(0, 2));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(0, 0, 4, 0));
        row.add(new JLabel(label), BorderLayout.NORTH);
        row.add(field, BorderLayout.CENTER);
        fillWidth(field);
        fillWidth(row);
        return row;
    }

    /**
     * Drops the preferred width so the component fills the sidebar instead of widening it; long
     * enum names and the text area's column count would otherwise overflow to the right.
     */
    private static void fillWidth(Component c) {
        int height = c.getPreferredSize().height;
        c.setPreferredSize(new Dimension(0, height));
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
    }
}
