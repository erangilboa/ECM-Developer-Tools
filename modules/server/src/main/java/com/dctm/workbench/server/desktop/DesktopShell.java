package com.dctm.workbench.server.desktop;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

public final class DesktopShell {
    private static final Logger log = LoggerFactory.getLogger(DesktopShell.class);

    private DesktopShell() {}

    public static boolean headless() {
        return GraphicsEnvironment.isHeadless();
    }

    public static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception e) {
            log.warn("Desktop browse failed: {}", e.getMessage());
        }
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("cmd", "/c", "start", "", url).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (Exception e) {
            log.warn("Could not open browser for {}: {}", url, e.getMessage());
        }
    }

    public static void showRunningWindow(String url, boolean openBrowser) {
        if (headless()) {
            if (openBrowser) {
                openBrowser(url);
            }
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // keep default L&F
            }
            if (openBrowser) {
                openBrowser(url);
            }
            JFrame frame = new JFrame("DCTM Workbench");
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    System.exit(0);
                }
            });

            JLabel title = new JLabel("Workbench is running");
            title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
            JLabel link = new JLabel(url);
            JPanel text = new JPanel(new BorderLayout(0, 6));
            text.setOpaque(false);
            text.add(title, BorderLayout.NORTH);
            text.add(link, BorderLayout.CENTER);

            JButton open = new JButton("Open in browser");
            open.addActionListener(e -> openBrowser(url));
            JButton quit = new JButton("Quit");
            quit.addActionListener(e -> System.exit(0));
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            buttons.setOpaque(false);
            buttons.add(open);
            buttons.add(quit);

            JPanel root = new JPanel(new BorderLayout(0, 12));
            root.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));
            root.add(text, BorderLayout.CENTER);
            root.add(buttons, BorderLayout.SOUTH);

            frame.setContentPane(root);
            frame.setSize(420, 150);
            frame.setResizable(false);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    public static void notifyAlreadyRunning(String url, boolean desktop) {
        openBrowser(url);
        if (!desktop || headless()) {
            return;
        }
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(
                        null,
                        "Workbench is already running.\n" + url,
                        "DCTM Workbench",
                        JOptionPane.INFORMATION_MESSAGE));
    }
}
