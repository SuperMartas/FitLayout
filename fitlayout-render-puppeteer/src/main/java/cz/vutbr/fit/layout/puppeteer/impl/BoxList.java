/**
 * BoxList.java
 *
 * Created on 13. 11. 2020, 9:38:30 by burgetr
 */
package cz.vutbr.fit.layout.puppeteer.impl;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.vutbr.fit.layout.impl.DefaultContentImage;
import cz.vutbr.fit.layout.model.Border;
import cz.vutbr.fit.layout.model.Box;
import cz.vutbr.fit.layout.model.Color;
import cz.vutbr.fit.layout.model.Rectangular;
import cz.vutbr.fit.layout.model.TextStyle;
import cz.vutbr.fit.layout.puppeteer.parser.Attribute;
import cz.vutbr.fit.layout.puppeteer.parser.BoxInfo;
import cz.vutbr.fit.layout.puppeteer.parser.ImageInfo;
import cz.vutbr.fit.layout.puppeteer.parser.InputFile;
import cz.vutbr.web.css.CSSException;
import cz.vutbr.web.css.CSSFactory;
import cz.vutbr.web.css.CSSProperty;
import cz.vutbr.web.css.Declaration;
import cz.vutbr.web.css.NodeData;
import cz.vutbr.web.css.RuleSet;
import cz.vutbr.web.css.StyleSheet;
import cz.vutbr.web.css.Term;
import cz.vutbr.web.css.TermColor;
import cz.vutbr.web.css.TermInteger;
import cz.vutbr.web.css.TermList;
import cz.vutbr.web.css.TermNumber;
import cz.vutbr.web.css.TermString;

/**
 * A list of FitLayout boxes created from the backend output.
 * 
 * @author burgetr
 */
public class BoxList
{
    private static Logger log = LoggerFactory.getLogger(BoxList.class);
    
    private static final int BOX_OFFSET = 1; //the index of the first real box in the list (excluding the artificial viewport)
    
    /** Accepted generic font families */
    private Set<String> defaultFonts = Set.of("serif", "sans-serif", "monospace");
    
    /** Font families available in the backend browser */ 
    private Set<String> availFonts;

    /** The viewport box used as a default parent */
    private BoxImpl viewport;
    
    /** Managed list of boxes */
    private List<Box> boxes;
    
    
    /**
     * Creates a box list from an input file obtained from the backend.
     * 
     * @param inputFile
     */
    public BoxList(InputFile inputFile)
    {
        availFonts = Set.of(inputFile.getFonts());
        viewport = createViewport(inputFile);
        createBoxList(inputFile);
        if (boxes.size() > BOX_OFFSET)
            updateViewport((BoxImpl) boxes.get(BOX_OFFSET));
        if (inputFile.getImages() != null)
            loadImages(inputFile.getImages());
    }

    /**
     * Gets the complete list of boxes.
     * @return
     */
    public List<Box> getBoxes()
    {
        return boxes;
    }
    
    /**
     * Gets a list of all visible boxes.
     * @return
     */
    public List<Box> getVisibleBoxes()
    {
        return boxes.stream()
                .filter(box -> box.isVisible())
                .collect(Collectors.toCollection(ArrayList::new));
    }
    
    //=======================================================================================
    
    private BoxImpl createViewport(InputFile input)
    {
        BoxImpl box = new BoxImpl(this);
        box.setOrder(0);
        box.setId(0);
        box.setTagName("viewport");
        box.setSourceNodeId("viewport");
        box.setIntrinsicBounds(new Rectangular(0, 0,
                Math.round(input.getPage().getWidth()), 
                Math.round(input.getPage().getHeight())));
        box.applyIntrinsicBounds();
        box.setAbsolute(true);
        box.setFontFamily(BoxTreeBuilder.DEFAULT_FONT_FAMILY);
        box.setColor(Color.BLACK);
        box.setBackgroundColor(Color.WHITE);
        return box;
    }
    
    /**
     * Updates the viewport using the body properties
     */
    private void updateViewport(BoxImpl body)
    {
        if (body.getBackgroundColor() != null)
            viewport.setBackgroundColor(body.getBackgroundColor());
        viewport.setColor(body.getColor());
    }
    
    private List<Box> createBoxList(InputFile input)
    {
        boxes = new ArrayList<>();
        //add the viewport as the first item
        boxes.add(viewport);
        int nextOrder = BOX_OFFSET;
        //create the element and text boxes
        for (BoxInfo boxInfo : input.getBoxes())
        {
            final NodeData style = parseCss(boxInfo.getCss());
            final BoxImpl newbox;
            if (boxInfo.getText() == null)
            {
                //a standard element box
                newbox = createElementBox(boxInfo, style, nextOrder++);
            }
            else
            {
                //text boxes
                newbox = createTextBox(boxInfo, style, nextOrder++);
            }
            //assign xpath ID if present
            newbox.setSourceNodeId(boxInfo.getXpath());
            //map the offset parent if any, the coordinates are computed from the parent
            if (newbox.getOffsetParent() != null)
            {
                newbox.applyIntrinsicBounds();
                if (!newbox.getOffsetParent().isVisible())
                    newbox.setVisible(false);
            }
            //apply clipping when applicable
            if (newbox.getClipBox() != null)
            {
                final Rectangular iBounds = newbox.getIntrinsicBounds();
                final Rectangular clipBounds = newbox.getClipBox().getIntrinsicBounds();
                if (iBounds.isEmpty()) //for empty boxes, test the top-left corner only
                {
                    if (!clipBounds.contains(iBounds.getX1(), iBounds.getY1()))
                        newbox.setVisible(false);
                }
                else
                {
                    final Rectangular clipped = newbox.getIntrinsicBounds().intersection(clipBounds);
                    newbox.setBounds(clipped);
                    newbox.setContentBounds(new Rectangular(clipped));
                    if (clipped.isEmpty()) 
                        newbox.setVisible(false);
                }
            }
            //add the box
            boxes.add(newbox);
            //The first box is the root box. Ensure it has a background set.
            if (boxes.size() == 1)
            {
                if (newbox.getBackgroundColor() == null)
                    newbox.setBackgroundColor(Color.WHITE);
            }
        }
        return boxes;
    }
    
    /**
     * Creates an element box.
     * @param src the source box info 
     * @param style the parsed box style
     * @param order  index of the box in the list
     * @return the new box
     */
    private BoxImpl createElementBox(BoxInfo src, NodeData style, int order)
    {
        BoxImpl ret = new BoxImpl(this);
        setupCommonProperties(ret, src, style, order);
        setupParents(ret, src);
        if (src.getReplaced() != null && src.getReplaced())
            ret.setType(Box.Type.REPLACED_CONTENT);
        else
            ret.setType(Box.Type.ELEMENT);
        ret.setTagName(src.getTagName());
        
        if (src.attrs != null)
        {
            for (Attribute attr : src.attrs)
                ret.setAttribute(attr.getName(), attr.getValue());
        }
        
        CSSProperty.Display display = style.getProperty("display");
        if (display != null)
        {
            final Box.DisplayType displayType = Units.toDisplayType(display);
            if (displayType != null)
                ret.setDisplayType(displayType);
        }
        
        CSSProperty.Overflow overflowx = style.getProperty("overflow-x");
        CSSProperty.Overflow overflowy = style.getProperty("overflow-y");
        ret.setClipping((overflowx != null && overflowx != CSSProperty.Overflow.VISIBLE)
                || (overflowy != null && overflowy != CSSProperty.Overflow.VISIBLE));
        
        CSSProperty.BackgroundColor color = style.getProperty("background-color");
        if (color == CSSProperty.BackgroundColor.color)
        {
            TermColor colorVal = style.getValue(TermColor.class, "background-color", false);
            if (colorVal != null)
            {
                Color clr = Units.toColor(colorVal.getValue());
                if (clr.getAlpha() > 0)
                    ret.setBackgroundColor(clr); //represent transparent background as null background
            }
        }
        
        for (Border.Side side : Border.Side.values())
        {
            Border brd = new CSSBorder(style, side.toString());
            if (brd.getStyle() != Border.Style.NONE)
                ret.setBorderStyle(side, brd);
        }
        
        return ret;
    }

    /**
     * Creates a text box.
     * @param src the source box info (its getText() value must not be null) 
     * @param style the parsed box style
     * @param order  index of the box in the list
     * @return the new box
     */
    private BoxImpl createTextBox(BoxInfo src, NodeData style, int order)
    {
        BoxImpl ret = new BoxImpl(this);
        setupCommonProperties(ret, src, style, order);
        setupParents(ret, src);
        ret.setType(Box.Type.TEXT_CONTENT);
        
        if (src.getText() != null)
        {
            TextStyle tstyle = new CSSTextStyle(src, style, src.getText().length());
            ((BoxImpl) ret).setIntrinsicTextStyle(tstyle);
            ret.setTextStyle(tstyle);
            ret.setOwnText(src.getText());
        }
        return ret;
    }
    
    /**
     * Sets the common properties of the box based on the style.
     * @param box the destination box
     * @param boxInfo source box info
     * @param style the parsed box style
     * @param order index of the box in the list
     */
    private void setupCommonProperties(BoxImpl box, BoxInfo boxInfo, NodeData style, int order)
    {
        box.setOrder(order);
        box.setId(order);
        
        final Rectangular ibounds = new Rectangular(
                Math.round(boxInfo.getX()),
                Math.round(boxInfo.getY()),
                Math.round(boxInfo.getX() + boxInfo.getWidth() - 1),
                Math.round(boxInfo.getY() + boxInfo.getHeight() - 1),
                false); //preserve the order of coordinates 
        box.setIntrinsicBounds(ibounds);
        box.applyIntrinsicBounds();
        
        //mark the elements outside of the viewport as invisible
        if (ibounds.isEmpty()) //treat zero width/height boxes separately
        {
            if (boxInfo.getText() != null)
            {
                //zero width/height text boxes are invisible
                box.setVisible(false); 
            }
            else
            {
                //element boxes are invisible only if they are outside of the viewport
                //otherwise, they may contain visible children (e.g. overflowing child boxes)
                if (!viewport.getIntrinsicBounds().contains(ibounds.getX1(), ibounds.getY1()))
                    box.setVisible(false);
            }
        }
        else if (!viewport.getIntrinsicBounds().intersects(ibounds)) //non-zero boxes must intersect with viewport
        {
            box.setVisible(false);
        }
        
        //check the visibility and opacity for finding hidden elements
        CSSProperty.Visibility visibility = style.getProperty("visibility");
        if (visibility == CSSProperty.Visibility.HIDDEN || visibility == CSSProperty.Visibility.COLLAPSE)
            box.setVisible(false);
        CSSProperty.Opacity opacity = style.getProperty("opacity");
        if (opacity == CSSProperty.Opacity.number)
        {
            Term<?> num = style.getValue("opacity", false);
            if (num != null)
            {
                if ((num instanceof TermInteger && ((TermInteger) num).getIntValue() == 0)
                        || (num instanceof TermNumber && ((TermNumber) num).getValue() < 0.01f))
                    box.setVisible(false);
            }
        }
        
        //check special position values
        CSSProperty.Position pos = style.getProperty("position");
        if (pos == CSSProperty.Position.ABSOLUTE)
            box.setAbsolute(true);
        else if (pos == CSSProperty.Position.FIXED)
            box.setFixed(true);
        
        box.setFontFamily(getUsedFont(style, BoxTreeBuilder.DEFAULT_FONT_FAMILY));
        
        CSSProperty.Color color = style.getProperty("color");
        if (color == CSSProperty.Color.color)
        {
            TermColor colorVal = style.getValue(TermColor.class, "color", false);
            if (colorVal != null)
                box.setColor(Units.toColor(colorVal.getValue()));
        }
    }
    
    /**
     * Finds and sets the DOM and offset parent for the box. Moreover, the intrinsic parent is set
     * accordinggly: for absolute positoned boxes, the offsetParent is used. For fixed elements,
     * the root box is used. Otherwise, the DOM parent is used.
     * @param box the destination box to which the parents should be assigned
     * @param boxInfo the source box info
     */
    private void setupParents(BoxImpl box, BoxInfo boxInfo)
    {
        if (boxInfo.getParent() != null)
        {
            final int pindex = boxInfo.getParent() + BOX_OFFSET;
            if (pindex < boxes.size())
            {
                final BoxImpl parent = (BoxImpl) boxes.get(pindex);
                box.setOffsetParent(parent);
            }
            else
            {
                log.error("Backend data error: the offset parent element <{}> is not available for <{}>.", pindex, boxInfo.getId());
            }
        }
        else
        {
            box.setOffsetParent(viewport); //no parent specified, use the viewport
        }
        
        if (boxInfo.getDomParent() != null)
        {
            final int pindex = boxInfo.getDomParent() + BOX_OFFSET;
            if (pindex < boxes.size())
            {
                final BoxImpl parent = (BoxImpl) boxes.get(pindex);
                box.setDomParent(parent);
            }
            else
            {
                log.error("Backend data error: the DOM parent element <{}> is not available for <{}>.", pindex, boxInfo.getId());
            }
        }
        else
        {
            box.setDomParent(viewport); //no parent specified, use the viewport
        }
        
        //compute the intrinsic parent
        if (box.isAbsolute())
        {
            if (box.getOffsetParent() != null)
                box.setIntrinsicParent(box.getOffsetParent());
            else
                log.error("Backend data error: absolutely positioned box <{}> has no offset parent", box.getOrder());
        }
        else if (box.isFixed())
        {
            if (boxes.size() > 0)
                box.setIntrinsicParent(viewport); //use the viewport box
            else
                log.warn("Backend data warning: root box <{}> has a fixed position", box.getOrder());
        }
        else
        {
            if (box.getDomParent() != null)
                box.setIntrinsicParent(box.getDomParent());
            else if (boxes.size() > 0)
                log.error("Backend data error: absolutely positioned box <{}> has no DOM parent", box.getOrder());
        }
    }
    
    /**
     * Parses the font-family declaration and finds the effective font used.
     * @param style
     * @param fallback
     * @return
     */
    private String getUsedFont(NodeData style, String fallback)
    {
        CSSProperty.FontFamily ff = style.getProperty("font-family");
        if (ff == CSSProperty.FontFamily.list_values)
        {
            TermList values = (TermList) style.getValue("font-family", false);
            for (Term<?> value : values)
            {
                if (value instanceof TermString)
                {
                    final String name = ((TermString) value).getValue();
                    if (availFonts.contains(name) || defaultFonts.contains(name))
                        return name;
                }
            }
            return fallback;
        }
        else
        {
            if (ff != null)
                return ff.toString();
            else
                return fallback;
        }
    }

    private NodeData parseCss(String css) 
    {
        String ssheet = "* { " + css + "}";
        NodeData style = CSSFactory.createNodeData();
        try {
            StyleSheet sheet = CSSFactory.parseString(ssheet, new URL("http://base.url"));
            RuleSet rule = (RuleSet) sheet.get(0);
            for (Declaration d : rule)
            {
                style.push(d);
            }
        } catch (CSSException e) {
            log.error("Couldn't parse inline css: {}", e.getMessage());
        } catch (MalformedURLException e) {
        } catch (IOException e) {
        }
        return style;
    }
    
    private void loadImages(ImageInfo[] images)
    {
        for (ImageInfo img : images)
        {
            if (img.getData() != null && img.getBg() != null 
                    && img.getId() != null && img.getId() >= 0 && img.getId() < boxes.size())
            {
                Box box = boxes.get(img.getId());
                try {
                    byte[] imgdata = Base64.getDecoder().decode(img.getData());
                    if (img.getBg())
                    {
                        ((BoxImpl) box).setBackgroundImagePng(imgdata);
                    }
                    else
                    {
                        DefaultContentImage cimg = new DefaultContentImage();
                        cimg.setPngData(imgdata);
                        ((BoxImpl) box).setContentObject(cimg);
                    }
                } catch (IllegalArgumentException e) {
                    log.error("Couldn't decode background image for id={}", img.getId());
                }
            }
        }
    }
    
}
