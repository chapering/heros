package heros.debugui.drawing;

import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Path;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

@SuppressWarnings("restriction")
public class EditorPaintListener implements PaintListener {
		
	protected final StyledText textWidget;
	protected final IJavaElement viewPartInput;

	public EditorPaintListener(JavaEditor javaEditor) {
		textWidget = javaEditor.getViewer().getTextWidget();
		viewPartInput = (IJavaElement) javaEditor.getViewPartInput();
	}

	public static void register() {
		IWorkbenchPage page = null;
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		if (window != null) {
			page = window.getActivePage();
		}
		if (page == null) {
			// Look for a window and get the page off it!
			IWorkbenchWindow[] windows = PlatformUI.getWorkbench().getWorkbenchWindows();
			for (int i = 0; i < windows.length; i++) {
				if (windows[i] != null) {
					window = windows[i];
					page = windows[i].getActivePage();
					if (page != null)
						break;
				}
			}
		}
		if(page!=null) {
			page.addPartListener(new IPartListener2(){

				@Override
				public void partActivated(IWorkbenchPartReference partRef) {
				}

				@Override
				public void partBroughtToTop(IWorkbenchPartReference partRef) {
				}

				@Override
				public void partClosed(IWorkbenchPartReference partRef) {
				}

				@Override
				public void partDeactivated(IWorkbenchPartReference partRef) {
				}

				@Override
				public void partOpened(IWorkbenchPartReference partRef) {
					if(partRef instanceof IEditorReference) {
						IEditorReference editorReference = (IEditorReference) partRef;
						IEditorPart editor = editorReference.getEditor(false);
						if(editor instanceof JavaEditor) {
							JavaEditor javaEditor = (JavaEditor) editor;
							StyledText textWidget = javaEditor.getViewer().getTextWidget();
							textWidget.addPaintListener(new EditorPaintListener(javaEditor));
						}
					}
				}

				@Override
				public void partHidden(IWorkbenchPartReference partRef) {
				}

				@Override
				public void partVisible(IWorkbenchPartReference partRef) {
				}

				@Override
				public void partInputChanged(IWorkbenchPartReference partRef) {
				}
				
			});
		}
		//TODO unregister listener anywhere?
	}

	@Override
	public void paintControl(PaintEvent e) {
		final int charHeight = textWidget.getLineHeight();
		final int charWidth = e.gc.textExtent("a").x;
		drawArrows(e, charHeight, charWidth);		
	}

	private void drawArrows(PaintEvent e, int charHeight, int charWidth) {
		IPath path = viewPartInput.getPath();
		IResource resource = ResourcesPlugin.getWorkspace().getRoot().findMember(path);
		String fileOSPath = resource.getLocation().toOSString();
		
		List<SPArrow> arrows = SourcePainterRegistry.getArrowsOfFile(fileOSPath);
		
		// Setup environment
		e.gc.setLineWidth(2);
		e.gc.setAlpha(150);
		e.gc.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_BLUE));
		e.gc.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_BLUE));
	
		if (arrows != null) {
			for (SPArrow arrow : arrows) {
	
				// Draw arrow
				int line1 = arrow.startL;
				int column1 = arrow.startC;
				int line2 = arrow.endL;
				int column2 = arrow.endC;
	
				if (line1 == line2 && column1 == column2) {
					System.err.println("Cannot draw arrow to self");
					continue;
				}
	
				// Decide the direction in which the arrow goes 
				int isDownwards = (line2 - line1 >= 0) ? 1 : -1;
				int isRightwards = (column2 - column1 >= 0) ? 1 : -1;
	
				// Get start and end points of the arrow
				int startOffset = getOffset(line1, column1);
				int endOffset = getOffset(line2, column2);
	
				// Remove invalid arrows
				if (startOffset < 0 || endOffset < 0) {
					System.err.println("Arrow " + arrow.toString() + " is invalid");
					SourcePainterRegistry.trashArrow(arrow);
					continue;
				}
	
				// Calculate startPoint, bezierPoint and endPoint
				Point startPoint = textWidget.getLocationAtOffset(startOffset);
				Point endPoint = textWidget.getLocationAtOffset(endOffset);
	
				
				Point bezierPoint;
				Point arcCenter;
	
				if (column1 == column2) {
					// Vertical arrow
					startPoint = new Point(startPoint.x, startPoint.y + charHeight / 2);
					endPoint = new Point(endPoint.x, endPoint.y + charHeight / 2);
					arcCenter = new Point((startPoint.x + endPoint.x) / 2, (startPoint.y + endPoint.y) / 2); 
					bezierPoint = new Point(arcCenter.x - 50, arcCenter.y);
	
				} else if (line1 == line2 || 
						Math.abs((double)(endPoint.y - startPoint.y) / (double)(endPoint.x - startPoint.x)) < 0.3) {
					// Horizontal arrow or near horizontal arrow
					startPoint = new Point(startPoint.x + charWidth / 2, startPoint.y + charHeight / 2);
					endPoint = new Point(endPoint.x + charWidth, endPoint.y + charHeight / 2);
					arcCenter = new Point((startPoint.x + endPoint.x) / 2, (startPoint.y + endPoint.y) / 2); 
	
					int yDelta = (int) (Math.abs(endPoint.x - startPoint.x) * 0.2);
					yDelta = Math.min(15, yDelta);
					yDelta = Math.max(50, yDelta);
					bezierPoint = new Point(arcCenter.x, arcCenter.y - yDelta); // 20
	
				}
				else {
					// New pos1.y: if is downwards adds charHeight, else doesn't change
					startPoint = new Point(startPoint.x + charWidth / 2, 
							startPoint.y + (charHeight - 3) * (isDownwards + 1) / 2 + 1 * (-isDownwards + 1) / 2);
					// New pos2.x: if is leftwards adds charWidth, else doesn't change
					endPoint = new Point(endPoint.x + charWidth * (-isRightwards + 1) / 2, endPoint.y + charHeight / 2);
	
	
					arcCenter = new Point((startPoint.x + endPoint.x) / 2, (startPoint.y + endPoint.y) / 2); 
					float lengthB = Math.abs(endPoint.x - arcCenter.x);
	
					double lengthC = Math.sqrt(Math.pow(arcCenter.x - startPoint.x, 2) + Math.pow(arcCenter.y - startPoint.y, 2));
					int startAngle = (int) (Math.round(Math.toDegrees(Math.acos(lengthB / lengthC)))) + 90; // in degrees
	
					// Triangle used to calculate the middle point of the curve
					double lengthCCurve = lengthC * 0.618;
					double lengthBCurve = Math.cos(Math.toRadians(startAngle - 90)) * lengthCCurve; 
					double lengthACurve = Math.sin(Math.toRadians(startAngle - 90)) * lengthCCurve;
	
					bezierPoint = new Point((int)(arcCenter.x - isRightwards * lengthBCurve), 
							(int)(arcCenter.y + isDownwards * lengthACurve));
				}
	
				// Prepare arrow head triangle
				float lengthHeadAlphaA = endPoint.x - bezierPoint.x;
				float lengthHeadAlphaB = endPoint.y - bezierPoint.y;
				double headAngleAlpha = Math.toDegrees(Math.atan(lengthHeadAlphaA / lengthHeadAlphaB)); // in degrees
	
				// beta = 90.0 - (30.0 - headAngleAlpha)
				double headAngleBeta = 60.0 + headAngleAlpha;
				double lengthHeadBetaA = Math.sin(Math.toRadians(headAngleBeta)) * 10;
				double lengthHeadBetaB = Math.cos(Math.toRadians(headAngleBeta)) * 10;
	
				// X: y1 < y2 -> + , y1 > y2 -> - 
				double triUpperRightX = endPoint.x + isDownwards * lengthHeadBetaB;
				double triUpperRightY = endPoint.y - isDownwards * lengthHeadBetaA;
	
				// gamma = 90.0 - headAngleAlpha - 30.0
				double headAngleGamma = 60.0 - headAngleAlpha;
				double lengthHeadGammaA = Math.sin(Math.toRadians(headAngleGamma)) * 10;
				double lengthHeadGammaB = Math.cos(Math.toRadians(headAngleGamma)) * 10;
	
				double triUpperLeftX = endPoint.x - isDownwards * lengthHeadGammaB;
				double triUpperLeftY = endPoint.y - isDownwards * lengthHeadGammaA;
	
	
				// Draw the arrow
				// Draw arrow shaft
				Path p = new Path(textWidget.getDisplay());
				p.moveTo(startPoint.x, startPoint.y);
				p.quadTo(bezierPoint.x, bezierPoint.y, endPoint.x, endPoint.y);
	
				e.gc.drawPath(p);
	
				p.dispose();
	
				// Draw arrow head
				p = new Path(textWidget.getDisplay());
				p.moveTo(endPoint.x, endPoint.y);
				p.lineTo((float)triUpperRightX, (float)triUpperRightY);
				p.lineTo((float)triUpperLeftX, (float)triUpperLeftY);
				p.close();
				e.gc.fillPath(p);
				p.dispose();
			}
			SourcePainterRegistry.emptyTrashOfArrows();
		}
		
		textWidget.redraw();
	}	
	
	/**
	 * Returns the offset in code given line and column. Notice that 
	 * line and column are 0-based.
	 * 
	 * @param line line
	 * @param column column
	 */
	private int getOffset(int line, int column)
	{
		if (line >= textWidget.getLineCount()) return -1;

		int offset = textWidget.getOffsetAtLine(line);
		
		offset += column;

		if (line == textWidget.getLineCount() - 1) {
			if (offset >= textWidget.getCharCount())
				offset = -1; 
		} else if (offset >= textWidget.getOffsetAtLine(line + 1)) 
			offset = -1;

		return offset;
	}



}
