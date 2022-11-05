    package com.rsm.reproducer;

    import burp.api.montoya.BurpExtension;
    import burp.api.montoya.MontoyaApi;
    import burp.api.montoya.http.message.HttpRequestResponse;
    import burp.api.montoya.http.message.requests.HttpRequest;
    import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
    import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
    import javax.swing.*;
    import static java.util.Collections.emptyList;

    import java.awt.*;
    import java.awt.datatransfer.Clipboard;
    import java.awt.datatransfer.StringSelection;
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
                    JMenuItem sendToReproducerMenuItem = new JMenuItem("Send to Reproducer");
                    sendToReproducerMenuItem.addActionListener(e -> {
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
                    JMenuItem copyPowerShellMenuItem = new JMenuItem("Copy as PowerShell command");
                    copyPowerShellMenuItem.addActionListener(e -> {
                        HttpRequest request = event.selectedRequestResponses().get(0).httpRequest();
                        PowerShellBuilder prb = new PowerShellBuilder(api);
                        StringSelection stringSelection = new StringSelection(prb.build(request).toString());
                        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                        clipboard.setContents(stringSelection, null);
                    });
                    JMenuItem copyPythonRequestsMenuItem = new JMenuItem("Copy as Python Requests command");
                    copyPythonRequestsMenuItem.addActionListener(e -> {
                        HttpRequest request = event.selectedRequestResponses().get(0).httpRequest();
                        PythonRequestBuilder prb = new PythonRequestBuilder(api);
                        StringSelection stringSelection = new StringSelection(prb.build(request).toString());
                        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                        clipboard.setContents(stringSelection, null);
                    });
                    JMenuItem copyJavaScriptFetchMenuItem = new JMenuItem("Copy as JavaScript Fetch command");
                    copyJavaScriptFetchMenuItem.addActionListener(e -> {
                        HttpRequest request = event.selectedRequestResponses().get(0).httpRequest();
                        JavaScriptRequestBuilder jrb = new JavaScriptRequestBuilder(api);
                        StringSelection stringSelection = new StringSelection(jrb.build(request).toString());
                        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                        clipboard.setContents(stringSelection, null);
                    });
                    return List.of(sendToReproducerMenuItem, copyPowerShellMenuItem, copyPythonRequestsMenuItem, copyJavaScriptFetchMenuItem);
                }
                else if (event.messageEditorRequestResponse().isPresent()) {
                    JMenuItem sendToReproducerMenuItem = new JMenuItem("Send to Reproducer");
                    sendToReproducerMenuItem.addActionListener(e -> {
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
                    JMenuItem copyPowerShellMenuItem = new JMenuItem("Copy as PowerShell command");
                    copyPowerShellMenuItem.addActionListener(e -> {
                        HttpRequest request = event.messageEditorRequestResponse().get().getRequestResponse().httpRequest();
                        PowerShellBuilder prb = new PowerShellBuilder(api);
                        StringSelection stringSelection = new StringSelection(prb.build(request).toString());
                        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                        clipboard.setContents(stringSelection, null);
                    });
                    JMenuItem copyPythonRequestsMenuItem = new JMenuItem("Copy as Python Requests command");
                    copyPythonRequestsMenuItem.addActionListener(e -> {
                        HttpRequest request = event.messageEditorRequestResponse().get().getRequestResponse().httpRequest();
                        PythonRequestBuilder prb = new PythonRequestBuilder(api);
                        StringSelection stringSelection = new StringSelection(prb.build(request).toString());
                        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                        clipboard.setContents(stringSelection, null);
                    });
                    JMenuItem copyJavaScriptFetchMenuItem = new JMenuItem("Copy as JavaScript Fetch command");
                    copyJavaScriptFetchMenuItem.addActionListener(e -> {
                        HttpRequest request = event.messageEditorRequestResponse().get().getRequestResponse().httpRequest();
                        JavaScriptRequestBuilder jrb = new JavaScriptRequestBuilder(api);
                        StringSelection stringSelection = new StringSelection(jrb.build(request).toString());
                        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                        clipboard.setContents(stringSelection, null);
                    });
                    return List.of(sendToReproducerMenuItem, copyPowerShellMenuItem, copyPythonRequestsMenuItem, copyJavaScriptFetchMenuItem);
                }
                else
                {
                    return emptyList();
                }
            }
        }
    }
