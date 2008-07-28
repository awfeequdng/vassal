/*
 * $Id$
 *
 * Copyright (c) 2000-2003 by Rodney Kinney
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License (LGPL) as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, copies are available
 * at http://www.opensource.org.
 */
package VASSAL.build.widget;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.JPopupMenu;
import VASSAL.build.Buildable;
import VASSAL.build.Builder;
import VASSAL.build.Configurable;
import VASSAL.build.GameModule;
import VASSAL.build.GpIdSupport;
import VASSAL.build.Widget;
import VASSAL.build.module.Map;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.build.module.documentation.HelpWindow;
import VASSAL.build.module.documentation.HelpWindowExtension;
import VASSAL.build.module.map.MenuDisplayer;
import VASSAL.command.AddPiece;
import VASSAL.command.Command;
import VASSAL.configure.Configurer;
import VASSAL.counters.BasicPiece;
import VASSAL.counters.Decorator;
import VASSAL.counters.DragBuffer;
import VASSAL.counters.GamePiece;
import VASSAL.counters.KeyBuffer;
import VASSAL.counters.PieceCloner;
import VASSAL.counters.PieceDefiner;
import VASSAL.counters.PlaceMarker;
import VASSAL.counters.Properties;
import VASSAL.i18n.ComponentI18nData;

/**
 * A Component that displays a GamePiece.
 * 
 * Can be added to any Widget but cannot contain any children Keyboard input on
 * a PieceSlot is forwarded to the {@link GamePiece#keyEvent} method for the
 * PieceSlot's GamePiece. Clicking on a PieceSlot initiates a drag
 */
public class PieceSlot extends Widget implements MouseListener, KeyListener {
  public static final String GP_ID = "gpid";
  private GamePiece c;
  private String name;
  private String pieceDefinition;
  protected static Font FONT = new Font("Dialog", 0, 12);
  private javax.swing.JPanel panel;
  private int width, height;
  private String gpId = ""; // Unique PieceSlot Id
  private GpIdSupport gpidSupport;

  public PieceSlot() {
    panel = new PieceSlot.Panel();
    panel.addMouseListener(this);
    panel.addKeyListener(this);
  }

  private class Panel extends javax.swing.JPanel {
    private static final long serialVersionUID = 1L;

    public Panel() {
      super();
      setFocusTraversalKeysEnabled(false); 
    }

    public void paint(Graphics g) {
      PieceSlot.this.paint(g);
    }

    public Dimension getPreferredSize() {
      return PieceSlot.this.getPreferredSize();
    }
  }

  public PieceSlot(GamePiece p) {
    this();
    setPiece(p);
  }

  public void setPiece(GamePiece p) {
    c = p;
    if (c != null) {
      c.setPosition(new Point(panel.getSize().width / 2,
                              panel.getSize().height / 2));
      name = Decorator.getInnermost(c).getName();
    }
    panel.revalidate();
    panel.repaint();
    pieceDefinition = c == null ? null :
      GameModule.getGameModule().encode(new AddPiece(c));
  }

  public GamePiece getPiece() {
    if (c == null && pieceDefinition != null) {
      final AddPiece comm =
        (AddPiece) GameModule.getGameModule().decode(pieceDefinition);
      if (comm == null) {
        System.err.println("Couldn't build piece " + pieceDefinition);
        pieceDefinition = null;
      }
      else {
        c = comm.getTarget();
        c.setState(comm.getState());
        c.setPosition(new Point(panel.getSize().width / 2,
                                panel.getSize().height / 2));
      }
    }
    if (c != null) {
      c.setProperty(Properties.PIECE_ID, getGpId());
    }
    return c;
  }

  public void paint(Graphics g) {
    final Color c = g.getColor();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, panel.getSize().width, panel.getSize().height);
    g.setColor(c);

    if (getPiece() == null) {
      final FontMetrics fm = g.getFontMetrics();
      g.drawRect(0, 0, panel.getSize().width - 1, panel.getSize().height - 1);
      g.setFont(FONT);
      g.drawString(" nil ", 
        panel.getSize().width/2 - fm.stringWidth(" nil ")/2,
        panel.getSize().height/2);
    }
    else {
      getPiece().draw(g, panel.getSize().width / 2,
                         panel.getSize().height / 2, panel, 1.0);
      if (Boolean.TRUE.equals(getPiece().getProperty(Properties.SELECTED))) {
        BasicPiece.getHighlighter().draw(getPiece(), g,
          panel.getSize().width / 2, panel.getSize().height / 2, panel, 1.0);
      }
    }
  }

  public Dimension getPreferredSize() {
    if (c != null && panel.getGraphics() != null) {
//      c.draw(panel.getGraphics(), 0, 0, panel, 1.0);
      return c.boundingBox().getSize();
    }
    else {
      return new Dimension(width, height);
    }
  }

  public void mousePressed(MouseEvent e) {
    KeyBuffer.getBuffer().clear(); 
    Map.clearActiveMap(); 
    if (getPiece() != null) {
      KeyBuffer.getBuffer().add(getPiece());
    }

    panel.requestFocus();
    panel.repaint();

  }

  // Puts counter in DragBuffer. Call when mouse gesture recognized
  protected void startDrag() {

    // Recenter piece; panel may have been resized at some point resulting
    // in pieces with inaccurate positional information.
    getPiece().setPosition(new Point(panel.getSize().width / 2, panel.getSize().height / 2));

    // Erase selection border to avoid leaving selected after mouse dragged out
    getPiece().setProperty(Properties.SELECTED, null);
    panel.repaint();

    if (getPiece() != null) {
      KeyBuffer.getBuffer().clear();
      DragBuffer.getBuffer().clear();
      GamePiece newPiece = PieceCloner.getInstance().clonePiece(getPiece());
      newPiece.setProperty(Properties.PIECE_ID, getGpId());
      DragBuffer.getBuffer().add(newPiece);
    }
  }

  public void mouseReleased(MouseEvent e) {
    if (getPiece() != null && e.isMetaDown()) {
      JPopupMenu popup = MenuDisplayer.createPopup(getPiece());
      popup.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
        public void popupMenuCanceled(javax.swing.event.PopupMenuEvent evt) {
          panel.repaint();
        }

        public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent evt) {
          panel.repaint();
        }

        public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent evt) {
        }
      });
      popup.show(panel, e.getX(), e.getY());
    }

  }

  public void mouseClicked(MouseEvent e) {
  }

  public void mouseEntered(MouseEvent e) {
  }

  public void mouseExited(MouseEvent e) {
    KeyBuffer.getBuffer().remove(getPiece());
    panel.repaint();
  }

  public void keyPressed(KeyEvent e) {
    KeyBuffer.getBuffer().keyCommand(javax.swing.KeyStroke.getKeyStrokeForEvent(e));
    e.consume();
    panel.repaint();
  }

  public void keyTyped(KeyEvent e) {
    KeyBuffer.getBuffer().keyCommand(javax.swing.KeyStroke.getKeyStrokeForEvent(e));
    e.consume();
    panel.repaint();
  }

  public void keyReleased(KeyEvent e) {
    KeyBuffer.getBuffer().keyCommand(javax.swing.KeyStroke.getKeyStrokeForEvent(e));
    e.consume();
    panel.repaint();
  }

  public static String getConfigureTypeName() {
    return "Single piece";
  }

  public java.awt.Component getComponent() {
    return panel;
  }

  /**
   * When building a PieceSlot, the text contents of the XML element are parsed
   * into a String. The String is decoded using {@link GameModule#decode}. The
   * resulting {@link Command} should be an instance of {@link AddPiece}. The
   * piece referred to in the Command becomes the piece contained in the
   * PieceSlot
   */
  public void build(org.w3c.dom.Element e) {
    gpidSupport = GameModule.getGameModule().getGpIdSupport();
    if (e != null) {
      name = e.getAttribute(NAME);
      gpId = e.getAttribute(GP_ID) + "";
      if (name.length() == 0) {
        name = null;
      }
      try {
        width = Integer.parseInt(e.getAttribute(WIDTH));
        height = Integer.parseInt(e.getAttribute(HEIGHT));
      }
      // FIXME: review error message
      catch (NumberFormatException ex) {
        width = 60;
        height = 60;
      }
      pieceDefinition = Builder.getText(e);
      c = null;
    }
  }

  public void addTo(Buildable parent) {
    panel.setDropTarget(VASSAL.build.module.map.PieceMover.DragHandler.makeDropTarget(panel, DnDConstants.ACTION_MOVE, null));

    DragGestureListener dragGestureListener = new DragGestureListener() {
      public void dragGestureRecognized(DragGestureEvent dge) {
        startDrag();
        VASSAL.build.module.map.PieceMover.DragHandler.getTheDragHandler().dragGestureRecognized(dge);
      }
    };
    DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(panel, DnDConstants.ACTION_MOVE, dragGestureListener);
  }

  public org.w3c.dom.Element getBuildElement(org.w3c.dom.Document doc) {
    org.w3c.dom.Element el = doc.createElement(getClass().getName());
    String s = getConfigureName();
    if (s != null) {
      el.setAttribute(NAME, s);
    }
    el.setAttribute(GP_ID, gpId+"");
    el.setAttribute(WIDTH, getPreferredSize().width + "");
    el.setAttribute(HEIGHT, getPreferredSize().height + "");
    el.appendChild(doc.createTextNode(c == null ? pieceDefinition : GameModule.getGameModule().encode(new AddPiece(c))));
    return el;
  }

  public void removeFrom(Buildable parent) {
  }

  public String getConfigureName() {
    if (name != null) {
      return name;
    }
    else if (getPiece() != null) {
      return Decorator.getInnermost(getPiece()).getName();
    }
    else {
      return null;
    }
  }

  public HelpFile getHelpFile() {
    return HelpFile.getReferenceManualPage("GamePiece.htm");
  }

  public String[] getAttributeNames() {
    return new String[0];
  }

  public String[] getAttributeDescriptions() {
    return new String[0];
  }

  public Class<?>[] getAttributeTypes() {
    return new Class<?>[0];
  }

  public void setAttribute(String name, Object value) {
  }

  /**
   * @return an array of Configurer objects representing the Buildable children
   *         of this Configurable object
   */
  public Configurable[] getConfigureComponents() {
    return new Configurable[0];
  }

  /**
   * @return an array of Configurer objects representing all possible classes of
   *         Buildable children of this Configurable object
   */
  public Class[] getAllowableConfigureComponents() {
    return new Class[0];
  }

  /*
   * Redirect getAttributeValueString() to return the attribute
   * values for the enclosed pieces
   */
  public String getAttributeValueString(String attr) {
    return getI18nData().getLocalUntranslatedValue(attr);
  }
  

  public ComponentI18nData getI18nData() {
    /*
     * Piece can change due to editing, so cannot cache the I18nData
     */
   return new ComponentI18nData(this, getPiece());
  }
  
  public Configurer getConfigurer() {
    return new MyConfigurer(this);
  }

  /**
   * Update the gpid for this PieceSlot, using the given {@link GpIdSupport}
   * to generate the new id.
   */  
  public void updateGpId(GpIdSupport s) {
    gpidSupport = s;
    updateGpId();
  }
  
  /**
   * Allocate a new gpid to this PieceSlot, plus to any PlaceMarker or
   * Replace traits.
   */
  public void updateGpId() {
    gpId = gpidSupport.generateGpId();
    GamePiece piece = getPiece();
    updateGpId(piece);
    setPiece(piece);
  }
  
  /**
   * Allocate new gpid's in the given GamePiece
   * 
   * @param piece GamePiece
   */
  public void updateGpId(GamePiece piece) {
    if (piece == null || piece instanceof BasicPiece) {
      return;
    }
    if (piece instanceof PlaceMarker) {
      ((PlaceMarker) piece).setGpId(gpidSupport.generateGpId());      
    }
    updateGpId(((Decorator) piece).getInner());
  }
  
  public String getGpId() {
    return gpId;
  }

  public void setGpId(String id) {
    gpId = id;
  }
  
  private static class MyConfigurer extends Configurer implements HelpWindowExtension {
    private PieceDefiner definer;

    public MyConfigurer(PieceSlot slot) {
      super(null, slot.getConfigureName(), slot);
      definer = new PieceDefiner(slot.getGpId(), slot.gpidSupport);
      definer.setPiece(slot.getPiece());
    }

    @Deprecated
    public void setBaseWindow(HelpWindow w) {
    }

    public String getValueString() {
      return null;
    }

    public void setValue(String s) {
      throw new UnsupportedOperationException("Cannot set from String");
    }

    public Object getValue() {
      PieceSlot slot = (PieceSlot) super.getValue();
      if (slot != null) {
        slot.setPiece(definer.getPiece());
      }
      return slot;
    }

    public java.awt.Component getControls() {
      return definer;
    }
  }
}
