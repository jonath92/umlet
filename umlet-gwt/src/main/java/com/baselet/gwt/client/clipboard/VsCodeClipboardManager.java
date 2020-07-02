package com.baselet.gwt.client.clipboard;

import com.baselet.control.basics.geom.Point;
import com.baselet.element.interfaces.Diagram;
import com.baselet.element.interfaces.GridElement;
import com.baselet.gwt.client.element.DiagramXmlParser;
import com.baselet.gwt.client.element.ElementFactoryGwt;
import com.baselet.gwt.client.view.*;
import com.google.gwt.core.client.GWT;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.baselet.gwt.client.view.widgets.DownloadPopupPanel.exportPngVSCode;

//TODO: Consider renaming to communicationManager since its responsibilities are more thann just the clipboard
public class VsCodeClipboardManager {
    private static EventHandlingUtils.DragCache storage; //needed to get active diagram
    private static CommandInvoker commandInvoker = CommandInvoker.getInstance();
    private static DrawPanelDiagram drawPanelDiagram;

    public static native void hookUpClipboardManagerToVsCode() /*-{
        $wnd.CopyDiagramToClipboard =
            $entry(@com.baselet.gwt.client.clipboard.VsCodeClipboardManager::copyDiagramToClipboard());
        $wnd.PasteClipboardToDiagram =
            $entry(@com.baselet.gwt.client.clipboard.VsCodeClipboardManager::pasteClipboardToDiagram(Ljava/lang/String;));
        $wnd.CutDiagramToClipboard =
            $entry(@com.baselet.gwt.client.clipboard.VsCodeClipboardManager::cutDiagramToClipboard());
        $wnd.HandleExportDiagramRequest =
            $entry(@com.baselet.gwt.client.clipboard.VsCodeClipboardManager::handleExportDiagramRequest(Ljava/lang/String;));
        $wnd.HandleUpdateContent =
            $entry(@com.baselet.gwt.client.clipboard.VsCodeClipboardManager::handleUpdateContent(Ljava/lang/String;));
        var newClipboardManager = {
            copy: function () {
                console.log("hit copy in gwt");
                $wnd.CopyDiagramToClipboard();
            },
            paste: function (content) {
                console.log("hit paste in gwt, content is: " + content);
                $wnd.PasteClipboardToDiagram(content);
            },
            cut: function () {
                console.log("hit cut in gwt");
                $wnd.CutDiagramToClipboard();
            },
            requestExport: function (content) {
                console.log("requesting export in gwt");
                $wnd.HandleExportDiagramRequest(content);
            },
            updateContent: function (content) {
                console.log("hit update in gwt, content is: " + content);
                $wnd.HandleUpdateContent(content);
            }
        };
        console.log("Sending clipboarmanager to gwt");
        window.parent.vsCodeClipboardManager = newClipboardManager;
    }-*/;

    /*
        since pastes in the vscode version will not trigger instantly, but rather request vscode to perform a paste action, the information on where a paste context
        menu is/was will be gone until the paste command arrives, since GWT removes the context window just before vs code has a chance to execute its paste command.
        Therefore, a prefered next position can be set with setNextPastePosition, nextPastePosition will be set to NULL with the next paste
     */
    private static Point nextPastePosition;
    public static void setNextPastePosition(Point nextPastePosition)
    {
        VsCodeClipboardManager.nextPastePosition = nextPastePosition;
    }

    //gets the previously set input position and sets it back to null
    public static Point popNextPastePosition()
    {
        Point returnpos = nextPastePosition;
        nextPastePosition = null;
        return returnpos;
    }

    public static void handleUpdateContent(String content)
    {
        try {
            drawPanelDiagram.setDiagram(DiagramXmlParser.xmlToDiagram(content));
        } catch (Exception e) {
            GWT.log("failed to load diagram passed from vscode, loading preset empty diagram defaults...");
        }
    }

    public static native void debugLogInVsCodeWebview(String content) /*-{
        console.log("GWT DEBUG LOG: " + content);
    }-*/;

    public static void setStorage(EventHandlingUtils.DragCache storage) {
        VsCodeClipboardManager.storage = storage;
    }

    public static void setDiagramPanel(DrawPanelDiagram drawPanelDiagram) {
        VsCodeClipboardManager.drawPanelDiagram = drawPanelDiagram;
    }

    //drawPanelDiagram must be set before this function is called
    //size must be a string which can be converted to a double
    public static void handleExportDiagramRequest(String size) {
        double scalingValue = Double.parseDouble(size);
        String scaledPngUrl = CanvasUtils.createPngCanvasDataUrl(drawPanelDiagram.getDiagram(), scalingValue);
        exportPngVSCode(scaledPngUrl);
    }

    //Without arguments default to whatever panel is active in storage
    public static void cutDiagramToClipboard() {
        if (VsCodeClipboardManager.storage.getActivePanel() instanceof DrawPanelDiagram) {
            DrawPanelDiagram activeDrawPanel = ((DrawPanelDiagram) VsCodeClipboardManager.storage.getActivePanel());
            commandInvoker.cutSelectedElements (activeDrawPanel);
        }
    }

    //Without arguments default to whatever panel is active in storage
    public static void copyDiagramToClipboard() {
        if (VsCodeClipboardManager.storage.getActivePanel() instanceof DrawPanel) {
            DrawPanel activeDrawPanel = ((DrawPanel) VsCodeClipboardManager.storage.getActivePanel());
            copyDiagramToClipboard(activeDrawPanel);
        }
    }


    public static void copyDiagramToClipboard(DrawPanel target) {
        List<GridElement> tempList = copyElementsInList(target.getSelector().getSelectedElements(), target.getDiagram());
        String dataForClipboard = (DiagramXmlParser.gridElementsToXml(tempList));
        setVsCodeClipboard(dataForClipboard);
    }

    private native static void setVsCodeClipboard(String content)
        /*-{
            window.parent.vscode.postMessage({
                command: 'setClipboard',
                text: content
            })
            console.log("COPIED3, wrote text to clip " + content);
        }-*/;

    public native static void requestVsCodePaste()
        /*-{
            window.parent.vscode.postMessage({
                command: 'requestPasteClipboard'
            })
        }-*/;



    private static List<GridElement> copyElementsInList(Collection<GridElement> sourceElements, Diagram targetDiagram) {
        List<GridElement> targetElements = new ArrayList<GridElement>();
        for (GridElement ge : sourceElements) {
            GridElement e = ElementFactoryGwt.create(ge, targetDiagram);
            targetElements.add(e);
        }
        return targetElements;
    }

    //assumes drawPanelDiagram was properly set before calling
    public static void pasteClipboardToDiagram(String content) {
        if (VsCodeClipboardManager.storage.getActivePanel() instanceof DrawPanelDiagram) {
            EventHandlingUtils.EventHandlingTarget lastEventHandlingTarget = VsCodeClipboardManager.storage.getActivePanel();
            //if there is no active DrawPanel (eg on a freshly opened tab) VsCode will just paste to diagram
            if (lastEventHandlingTarget == null) {
                lastEventHandlingTarget = drawPanelDiagram;
            }
            if (lastEventHandlingTarget instanceof DrawPanelDiagram) {
                DrawPanelDiagram activeDrawPanel = ((DrawPanelDiagram) lastEventHandlingTarget);
                pasteClipboardToDiagram(activeDrawPanel, content);
            }
        }
    }

    public static void pasteClipboardToDiagram(DrawPanelDiagram target, String content) {
        commandInvoker.pasteElementsVsCode(target, content);
        nextPastePosition = null;
    }
}