    package com.rsm.reproducer;

    import burp.api.montoya.BurpExtension;
    import burp.api.montoya.MontoyaApi;
    import burp.api.montoya.http.message.HttpRequestResponse;
    import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
    import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
    import javax.swing.*;
    import static java.util.Collections.emptyList;
    import java.util.List;

    public class Reproducer implements BurpExtension
    {
        private MontoyaApi api;
        private static final String VERSION = "1";
        private static final String EXTENSION_NAME = "Reproducer";
        ReproducerTab tab;

        @Override
        public void initialize(MontoyaApi api)
        {
            this.api = api;
            api.misc().setExtensionName(EXTENSION_NAME);
            api.logging().logToOutput(" == " + EXTENSION_NAME + " version " + VERSION + " == ");
            api.logging().logToOutput("Simplifier and formatter of requests to aid in finding reproduction and proof of concept creation.");
            api.logging().logToOutput("Created by Micah Van Deusen, RSM US LLP.");
            api.logging().logToOutput("  Credits to:");
            api.logging().logToOutput("    Copy as PowerShell Requests - Alexandre Teyar, Aegis Cyber");
            api.logging().logToOutput("    Copy As Python-Requests - Andras Veres-Szentkiralyi");
            api.logging().logToOutput("    Copy as JavaScript Request - Celso Gomes Bezerra");

            this.tab = new ReproducerTab(api);

            api.userInterface().registerSuiteTab(EXTENSION_NAME, tab.getMainPanel());
            api.userInterface().registerContextMenuItemsProvider(new ReproducerContextMenu());
        }
        private class ReproducerContextMenu implements ContextMenuItemsProvider
        {
            public List<JMenuItem> provideMenuItems(ContextMenuEvent event)
            {
                if (!event.selectedRequestResponses().isEmpty()) {
                    JMenuItem menuItem = new JMenuItem("Send to Reproducer");
                    menuItem.addActionListener(e -> {
                        tab.selectParentTab();
                        new Thread(() -> {
                            try {
                                for (HttpRequestResponse hrr : event.selectedRequestResponses()) {
                                    tab.addSelectionRequest(hrr);
                                }
                            }
                            catch(Exception ex)
                            {
                                ex.printStackTrace(api.logging().error());
                            }
                        }).start();
                    });
                    return List.of(menuItem);
                }
                else if (event.messageEditorRequestResponse().isPresent()) {
                    JMenuItem menuItem = new JMenuItem("Send to Reproducer");
                    menuItem.addActionListener(e -> {
                        tab.selectParentTab();
                        // This function may make HTTP calls, so run in a thread
                        new Thread(() -> {
                            try {
                                tab.addSelectionRequest(event.messageEditorRequestResponse().get().getRequestResponse());
                            }
                            catch(Exception ex)
                            {
                                ex.printStackTrace(api.logging().error());
                            }
                        }).start();
                    });
                    return List.of(menuItem);
                }
                else
                {
                    return emptyList();
                }
            }
        }
    }
