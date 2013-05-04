package com.stampysoft.photoGallery.admin;

import com.stampysoft.photoGallery.PhotoOperations;
import com.stampysoft.photoGallery.Photographer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class PhotographerDialog extends JDialog
{

    private JTextField _nameTextField = new JTextField(10);
    private JTextArea _copyrightTextArea = new JTextArea(5, 10);

    private JButton _deleteButton;

    private Photographer _photographer;

    public PhotographerDialog(JFrame parent)
    {
        super(parent, "Photographer Info");

        JPanel contentPanel = new JPanel(new GridBagLayout());

        GridBagConstraints labelGBC = new GridBagConstraints();
        labelGBC.anchor = GridBagConstraints.NORTHWEST;

        GridBagConstraints valueGBC = new GridBagConstraints();
        valueGBC.gridwidth = GridBagConstraints.REMAINDER;
        valueGBC.fill = GridBagConstraints.HORIZONTAL;
        valueGBC.weightx = 1.0;

        contentPanel.add(new JLabel("Name:"), labelGBC);
        contentPanel.add(_nameTextField, valueGBC);

        contentPanel.add(new JLabel("Copyright:"), labelGBC);
        _copyrightTextArea.setWrapStyleWord(true);
        _copyrightTextArea.setLineWrap(true);
        contentPanel.add(new JScrollPane(_copyrightTextArea), valueGBC);

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                PhotographerDialog.this.setVisible(false);
            }
        });

        JButton okButton = new JButton("OK");
        okButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                _photographer.setName(_nameTextField.getText());
                _photographer.setCopyright(_copyrightTextArea.getText());

                PhotoOperations.getPhotoOperations().savePhotographer(_photographer);
                AdminModel.getModel().firePhotographersChanged();
                setVisible(false);
            }
        });

        _deleteButton = new JButton("Delete");
        _deleteButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                if (JOptionPane.showConfirmDialog(_deleteButton, "Are you sure you want to delete that photographer?") == JOptionPane.YES_OPTION)
                {
                    PhotoOperations.getPhotoOperations().deletePhotographer(_photographer);
                    AdminModel.getModel().firePhotographersChanged();
                    setVisible(false);
                }
            }
        });

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        JPanel buttonPanel2 = new JPanel();
        buttonPanel2.add(_deleteButton);

        contentPanel.add(new JPanel(), labelGBC);
        contentPanel.add(buttonPanel, valueGBC);

        contentPanel.add(new JPanel(), labelGBC);
        contentPanel.add(buttonPanel2, valueGBC);

        setContentPane(contentPanel);
        pack();
    }

    public void showPhotographer(Photographer photographer)
    {
        _photographer = photographer;
        _deleteButton.setEnabled(_photographer.getPhotographerId() != null);
        _nameTextField.setText(_photographer.getName());
        _copyrightTextArea.setText(_photographer.getCopyright());
    }

    protected void handleException(Throwable t)
    {
        t.printStackTrace();
    }
}
