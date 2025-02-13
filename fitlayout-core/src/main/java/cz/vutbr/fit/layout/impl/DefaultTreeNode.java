/**
 * DefaultTreeNode.java
 *
 * Created on 19. 11. 2014, 21:46:53 by burgetr
 */
package cz.vutbr.fit.layout.impl;

import java.util.ArrayList;
import java.util.List;

import cz.vutbr.fit.layout.model.GenericTreeNode;

/**
 * A generic tree node used as a base for the Box and Area implementation.
 * 
 * @author burgetr
 */
public class DefaultTreeNode<T extends GenericTreeNode<T>> implements GenericTreeNode<T>
{
    private T myself;
    private T root;
    private T parent;
    private List<T> children;

    public DefaultTreeNode(Class<T> myType)
    {
        children = new ArrayList<>();
        parent = null;
        myself = myType.cast(this);
        root = myself;
    }

    @Override
    public T getParent()
    {
        return parent;
    }

    @Override
    public void setParent(T parent)
    {
        this.parent = parent;
    }
    
    @Override
    public T getRoot()
    {
        return root;
    }

    @Override
    public void setRoot(T root)
    {
        this.root = root;
    }

    @Override
    public boolean isRoot()
    {
        return (getRoot() == this);
    }
    
    @Override
    public List<T> getChildren()
    {
        return children;
    }

    @Override
    public int getChildCount()
    {
        return getChildren().size();
    }

    @Override
    public boolean isLeaf()
    {
        return (getChildCount() == 0);
    }

    private void addChild(T child)
    {
        if (child.getParent() != null)
            child.getParent().removeChild(child);
        child.setParent(myself);
        child.setRoot(getRoot());
        children.add(child);
    }
    
    @Override
    public void appendChild(T child)
    {
        addChild(child);
        childrenChanged();
    }

    @Override
    public void appendChildren(List<T> list)
    {
        for (T child : list)
            addChild(child);
        childrenChanged();
    }
    
    @Override
    public void insertChild(T child, int index)
            throws IndexOutOfBoundsException
    {
        if (child.getParent() != null)
            child.getParent().removeChild(child);
        child.setParent(myself);
        children.add(index, child);
        childrenChanged();
    }

    @Override
    public void removeAllChildren()
    {
        for (T child : children)
        {
            child.setParent(null);
            child.setRoot(child);
        }
        children.clear();
        childrenChanged();
    }

    @Override
    public void removeChild(int index) throws IndexOutOfBoundsException
    {
        T child = children.get(index); 
        child.setParent(null);
        child.setRoot(child);
        children.remove(index);
        childrenChanged();
    }

    @Override
    public void removeChild(T child) throws IllegalArgumentException
    {
        if (children.remove(child))
        {
            child.setParent(null);
            child.setRoot(child);
            childrenChanged();
        }
        else
            throw new IllegalArgumentException("Given node is not a child of this node");
    }

    @Override
    public T getChildAt(int index)
            throws IndexOutOfBoundsException
    {
        return children.get(index);
    }
    
    @Override
    public int getIndex(T child)
    {
    	if (child != null)
    		return children.indexOf(child);
    	else
    		throw new IllegalArgumentException("The child cannot be null");
    }
    
    @Override
    public T getPreviousSibling()
    {
    	if (getParent() != null)
    	{
    		int index = getParent().getIndex(myself);
    		if (index == 0)
    			return null;
    		else
    			return getParent().getChildAt(index - 1);
    	}
    	else
    		return null;
    }

    @Override
    public T getNextSibling()
    {
    	if (getParent() != null)
    	{
    		int index = getParent().getIndex(myself);
    		if (index == getParent().getChildCount() - 1)
    			return null;
    		else
    			return getParent().getChildAt(index + 1);
    	}
    	else
    		return null;
    }

    @Override
    public int getDepth()
    {
    	return recursiveGetDepth(myself);
    }
    
    private int recursiveGetDepth(T root)
    {
    	if (root.isLeaf())
    		return 0;
    	else
    	{
    		int max = 0;
    		for (T child : root.getChildren())
    		{
    			int cdepth = recursiveGetDepth(child);
    			if (cdepth > max)
    				max = cdepth;
    		}
    		return max + 1;
    	}
    }
    
    @Override
    public int getLeafCount()
    {
        return recursiveGetLeafCount(myself);
    }
    
    private int recursiveGetLeafCount(T root)
    {
        if (root.isLeaf())
            return 1;
        else
        {
            int sum = 0;
            for (T child : root.getChildren())
                sum += recursiveGetLeafCount(child);
            return sum;
        }
    }
    
    /**
     * This method is called after some child nodes have been added or removed.
     * Subclasses may override this method in order to update their own internal structures.
     */
    public void childrenChanged()
    {
        //do nothing by default
    }
    
}
