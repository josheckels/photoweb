package com.stampysoft.photoGallery.admin;

import com.stampysoft.gui.AbstractPanel;
import com.stampysoft.photoGallery.Photo;
import com.stampysoft.photoGallery.PhotoOperations;
import com.stampysoft.photoGallery.Photographer;
import com.stampysoft.util.SystemException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Objects;

public class PhotographerPanel extends AbstractPanel
{

    private JComboBox _photographersComboBox;
    private DefaultComboBoxModel _model;
    private JButton _editButton;
    private JButton _newButton;
    private boolean _mixed = false;
    private List<Photographer> _photographers;

    public PhotographerPanel()
    {
        super(new GridBagLayout());

        _model = new DefaultComboBoxModel();
        _photographersComboBox = new JComboBox(_model);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.weightx = 1.0;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(_photographersComboBox, gbc);

        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;

        _editButton = new JButton("Edit");
        _editButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                PhotographerDialog dialog = new PhotographerDialog(AdminFrame.getFrame());
                dialog.setSize(400, 400);
                dialog.showPhotographer((Photographer) _photographersComboBox.getSelectedItem());
                dialog.setVisible(true);
            }
        });
        add(_editButton, gbc);

        _newButton = new JButton("New");
        _newButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                PhotographerDialog dialog = new PhotographerDialog(AdminFrame.getFrame());
                dialog.setSize(400, 400);
                dialog.showPhotographer(new Photographer());
                dialog.setVisible(true);
            }
        });
        add(_newButton, gbc);

        AdminModel.getModel().addPhotographerListener(new PhotographerListener()
        {
            public void photographerListChanged()
            {
                try
                {
                    reload();
                }
                catch (SystemException e)
                {
                    handleException(e);
                }
            }

        });
    }

    public void refreshPhotos(Photo[] newPhotos)
    {
        if (_photographers == null)
        {
            doReload();
        }

        if (newPhotos.length == 0)
        {
            _editButton.setEnabled(false);
            _photographersComboBox.setEnabled(false);
            _editButton.setEnabled(false);
            _mixed = true;
            return;
        }

        _editButton.setEnabled(true);
        _photographersComboBox.setEnabled(true);
        _newButton.setEnabled(true);

        Integer photographerId = AdminFrame.getFrame().getPhotoOperations().getPhotographerIdOfPhoto(newPhotos[0]);

        boolean various = false;
        for (Photo photo : newPhotos)
        {
            Integer pid = AdminFrame.getFrame().getPhotoOperations().getPhotographerIdOfPhoto(photo);
            if (!Objects.equals(photographerId, pid))
            {
                various = true;
                break;
            }
        }

        if (various)
        {
            _photographersComboBox.insertItemAt(Photographer.VARIOUS_PHOTOGRAPHER, 0);
            _photographersComboBox.setSelectedIndex(0);
            _photographersComboBox.setEnabled(false);
            _editButton.setEnabled(false);
            _newButton.setEnabled(false);
            _mixed = true;
        }
        else if (photographerId == null)
        {
            _photographersComboBox.setSelectedItem(Photographer.UNKNOWN_PHOTOGRAPHER);
            _editButton.setEnabled(false);
            _newButton.setEnabled(false);
            _mixed = false;
        }
        else
        {
            _mixed = false;
            for (int i = 1; i < _model.getSize(); i++)
            {
                Photographer photographer2 = (Photographer) _model.getElementAt(i);
                if (photographer2 != null && Objects.equals(photographer2.getPhotographerId(), photographerId))
                {
                    _photographersComboBox.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    public void reload() throws SystemException
    {
        doReload();
        refreshPhotos(AdminModel.getModel().getCurrentPhotos());
    }


    public void doReload()
    {
        _model.removeAllElements();
        _photographers = PhotoOperations.getPhotoOperations().getAllPhotographers();
        _model.addElement(Photographer.UNKNOWN_PHOTOGRAPHER);
        for (Photographer photographer : _photographers)
        {
            _model.addElement(photographer);
        }
    }

    public boolean isMixed()
    {
        return _mixed;
    }

    public Photographer getSelected()
    {
        Photographer photographer = (Photographer) _photographersComboBox.getSelectedItem();
        if (photographer == Photographer.UNKNOWN_PHOTOGRAPHER)
        {
            return null;
        }
        return photographer;
    }
}
