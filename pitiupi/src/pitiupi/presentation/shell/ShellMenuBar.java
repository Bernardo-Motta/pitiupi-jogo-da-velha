package pitiupi.presentation.shell;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

/**
 * Barra de menus: Plugins (online) e Ajuda.
 */
public final class ShellMenuBar extends JMenuBar {

    public ShellMenuBar(java.awt.Frame owner) {
        JMenu plugins = new JMenu("Plugins");
        JMenuItem online = new JMenuItem("Plugins online…");
        online.addActionListener(e -> new RemotePluginsDialog(owner).setVisible(true));
        plugins.add(online);
        add(plugins);

        JMenu help = new JMenu("Ajuda");
        JMenuItem about = new JMenuItem("Sobre");
        about.addActionListener(e -> new AboutDialog().setVisible(true));
        help.add(about);
        add(help);
    }
}
