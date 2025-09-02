package com.stampysoft.photoGallery.admin;

import com.stampysoft.photoGallery.Category;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

public class CategorySelection extends TransferHandler
{

    private static final DataFlavor[] FLAVORS = {
        new DataFlavor(Category.class, "Category")};

    public int getSourceActions(JComponent c)
    {
        return TransferHandler.COPY;
    }

    public boolean canImport(JComponent comp, DataFlavor flavor[])
    {
        if (comp instanceof JList)
        {
            return ((JList) comp).getModel() instanceof CategoryListModel;
        }
        return false;
    }

    public Transferable createTransferable(JComponent comp)
    {
        if (comp instanceof JTree)
        {
            TreePath[] paths = ((JTree) comp).getSelectionPaths();
            java.util.List categories = new ArrayList(paths.length);
            for (int i = 0; i < paths.length; i++)
            {
                categories.add(((CategoryTreeNode) paths[i].getLastPathComponent()).getCategory());
            }
            return new CategoryTransferable(categories);
        }
        else if (comp instanceof JList)
        {
            Object[] values = ((JList) comp).getSelectedValues();
            java.util.List categories = new ArrayList(values.length);
            for (int i = 0; i < values.length; i++)
            {
                categories.add((Category) values[i]);
            }
            return new CategoryTransferable(categories);
        }
        return null;
    }

    public boolean importData(JComponent comp, Transferable t)
    {
        try
        {
            if (t.isDataFlavorSupported(FLAVORS[0]))
            {
                Iterator categories = ((java.util.List) t.getTransferData(FLAVORS[0])).iterator();
                if (comp instanceof JList)
                {
                    if (((JList) comp).getModel() instanceof CategoryListModel)
                    {
                        CategoryListModel listModel = (CategoryListModel) ((JList) comp).getModel();
                        while (categories.hasNext())
                        {
                            listModel.addCategory((Category) categories.next());
                        }
                        return true;
                    }
                }
            }
        }
        catch (UnsupportedFlavorException ignored)
        {
        }
        catch (IOException ignored)
        {
        }
        return false;
    }

    private class CategoryTransferable implements Transferable
    {
        private java.util.List _categories;

        public CategoryTransferable(java.util.List categories)
        {
            _categories = categories;
        }

        public Object getTransferData(DataFlavor flavor)
            throws UnsupportedFlavorException
        {
            if (isDataFlavorSupported(flavor))
            {
                return _categories;
            }
            throw new UnsupportedFlavorException(flavor);
        }

        public DataFlavor[] getTransferDataFlavors()
        {
            return FLAVORS;
        }

        public boolean isDataFlavorSupported(DataFlavor flavor)
        {
            return flavor.equals(FLAVORS[0]);
        }
    }
}