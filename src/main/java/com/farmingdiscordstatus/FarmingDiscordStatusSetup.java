package com.farmingdiscordstatus;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JDialog;
import javax.swing.WindowConstants;
import net.runelite.client.util.LinkBrowser;

final class FarmingDiscordStatusSetup
{
    private static final String DISCORD_INSTALL_URL =
        "https://discord.com/oauth2/authorize?client_id=1544867499378614363";

    private JDialog dialog;

    void close()
    {
        if (dialog != null)
        {
            dialog.dispose();
            dialog = null;
        }
    }

    void open(Runnable testPing)
    {
        if (dialog != null && dialog.isDisplayable())
        {
            dialog.toFront();
            return;
        }
        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setBorder(BorderFactory.createEmptyBorder(12, 10, 12, 10));
        JPanel setup = new JPanel(new BorderLayout(0, 12));
        setup.add(new JLabel("<html><body style='width:340px'>"
            + "<h3>Farming Discord Status</h3>"
            + "<p><b>First time? Follow these setup directions to link your account.</b></p>"
            + "<ol><li>Click <b>Link account</b> below and authorize the app in Discord.</li>"
            + "<li>DM <b>Farming Status</b> and run <b>/link</b>.</li>"
            + "<li>Paste the one-time code into <b>Link code</b> in this plugin's settings.</li>"
            + "<li>Enable <b>Link account</b> in settings once. The code clears after successful linking.</li></ol>"
            + "<p>Keep <b>Time Tracking</b> enabled and visit your patches while logged in to record timers.</p>"
            + "<p>Code not working? Run <b>/link</b> for a new one. Use <b>/unlink</b> in Discord to disconnect.</p>"
            + "</body></html>"), BorderLayout.CENTER);
        JButton link = new JButton("Link account");
        link.setToolTipText("Open Discord authorization to start linking your account");
        link.addActionListener(event -> LinkBrowser.browse(DISCORD_INSTALL_URL));
        setup.add(link, BorderLayout.SOUTH);
        content.add(setup, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new GridLayout(0, 1, 0, 8));

        JButton test = new JButton("Send test ping");
        test.addActionListener(event -> testPing.run());
        bottom.add(test);

        content.add(bottom, BorderLayout.SOUTH);
        dialog = new JDialog();
        dialog.setTitle("Farming Discord Status — Setup");
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setContentPane(content);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }
}
