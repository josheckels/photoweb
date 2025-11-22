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
import java.util.List;

public class CategorySelection extends TransferHandler
{

    private static final DataFlavor[] FLAVORS = {
        new DataFlavor(Category.class, "Category")};

    public int getSourceActions(JComponent c)
    {
        return TransferHandler.COPY;
    }

    public boolean canImport(JComponent comp, DataFlavor[] flavor)
    {
        if (comp instanceof JList<?> list)
        {
            return list.getModel() instanceof CategoryListModel;
        }
        return false;
    }

    public Transferable createTransferable(JComponent comp)
    {
        if (comp instanceof JTree tree)
        {
            TreePath[] paths = tree.getSelectionPaths();
            java.util.List<Category> categories = new ArrayList<>(paths.length);
            for (TreePath path : paths) {
                categories.add(((CategoryTreeNode) path.getLastPathComponent()).getCategory());
            }
            return new CategoryTransferable(categories);
        }
        else if (comp instanceof JList list)
        {
            List<Category> values = list.getSelectedValuesList();
            java.util.List<Category> categories = new ArrayList<>(values.size());
            categories.addAll(values);
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
                    if (((JList) comp).getModel() instanceof CategoryListModel listModel)
                    {
                        while (categories.hasNext())
                        {
                            listModel.addCategory((Category) categories.next());
                        }
                        return true;
                    }
                }
            }
        }
        catch (UnsupportedFlavorException | IOException ignored)
        {
        }
        return false;
    }

    private static class CategoryTransferable implements Transferable
    {
        private final java.util.List<Category> _categories;

        public CategoryTransferable(java.util.List<Category> categories)
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