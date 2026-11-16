package pitiupi.velha.plugin;

import pitiupi.api.plugin.PeerActionsRegistrar;
import pitiupi.api.plugin.PluginWorkspace;
import pitiupi.application.peeractions.PeerAction;
import pitiupi.application.peeractions.PeerActionTarget;
import pitiupi.application.peeractions.PeerTarget;
import pitiupi.control.ConnectionManager;
import pitiupi.models.Messages.Message;
import pitiupi.models.Plugin;
import pitiupi.velha.GUI.VelhaPanel;
import pitiupi.velha.control.VelhaController;
import pitiupi.velha.net.VelhaMessage;

import java.util.List;

/**
 * Jogo da Velha 1x1 entre dois usuários do pitiupi. O desafio é anunciado
 * para a sala e toda a comunicação é UDP multicast.
 */
public class VelhaPlugin extends Plugin {

    private PluginWorkspace workspace;
    private VelhaController controller;

    @Override
    public void createPlugin(ConnectionManager connectionManager, PluginWorkspace workspace) {
        this.workspace = workspace;
        this.controller = new VelhaController(connectionManager);
        bindShellWorkspaceContent(new VelhaPanel(controller));
        installPeerActions(workspace.peerActions());
    }

    @Override
    public void installPeerActions(PeerActionsRegistrar registrar) {
        registrar.register(PeerAction.of(
                "velha.sala",
                "Desafiar a sala para Jogo da Velha",
                PeerActionTarget.ALL_PEERS,
                target -> {
                    workspace.openOrFocusTab(getIdentifier(), getDisplayName());
                    controller.desafiar(null);
                }
        ));
        registrar.register(PeerAction.of(
                "velha.peer",
                "Desafiar para Jogo da Velha",
                PeerActionTarget.SINGLE_PEER,
                target -> {
                    workspace.openOrFocusTab(getIdentifier(), getDisplayName());
                    if (target instanceof PeerTarget.Single single && !single.peer().isSelf()) {
                        controller.desafiar(single.peer().getDisplayName());
                    }
                }
        ));
    }

    @Override
    public List<Class<? extends Message>> getMessages() {
        return List.of(VelhaMessage.class);
    }

    @Override
    public void receiveMessage(Message message) {
        if (message instanceof VelhaMessage m && controller != null) {
            controller.receber(m);
        }
    }

    @Override
    public String getName() {
        return "JogoDaVelha";
    }

    @Override
    public String getDisplayName() {
        return "Jogo da Velha";
    }

    @Override
    public String getDescription() {
        return "Jogo da Velha 1x1 via UDP multicast";
    }

    @Override
    public String getAuthor() {
        return "Bernardo Motta";
    }

    @Override
    public String getVersion() {
        return "1";
    }

    @Override
    public String getIdentifier() {
        return "pitiupi.velha";
    }
}
