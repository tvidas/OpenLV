/*
   Copyright (C) 2006-2008 Carnegie Mellon University

   Tim Vidas <tvidas at gmail d0t com>
   Brian Kaplan <bfkaplan at cmu d0t edu>


   This program is free software; you can redistribute it and/or modify it
   under the terms of the GNU General Public License as published by the Free
   Software Foundation; either version 2 of the License, or (at your option)
   any later version.

   This program is distributed in the hope that it will be useful, but WITHOUT
   ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
   FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
   more details.

   You should have received a copy of the GNU General Public License along with
   this program; if not, write to the Free Software Foundation, Inc., 59 Temple
   Place, Suite 330, Boston, MA 02111-1307 USA
   */

package cmu.forensics.openlv;


import java.io.*;

import javax.swing.*;

import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;

/**
 * SecondaryDiskMenu
 *
 * @author Tim Vidas
 * @author Brian Kaplan
 * @version 0.7, Jan 2009
 */

public class SecondaryDiskMenu extends JDialog
{
	private String displayText;			//text displayed in the combo box dropdown
	private String underlyingValue;		//value used internally for that displayed text
	private JFrame parent;
	public JPopupMenu popup;
	private String diskInfo;
	private String retVal = "";
	int theValue =0;
	private File workingDir;

	public SecondaryDiskMenu(JFrame p, File wd)//OpenLVLauncher p)//String displayText, String underlyingValue)
	{
		parent = p;
		workingDir = wd;
	}
	public DiskData showDialog(){
		final DiskData dd = new DiskData();

		final JDialog win = new JDialog(parent);

		win.setTitle("Add a data disk");
		win.setModal(true);

		//Set the frame's icon to an image loaded from a file.
		win.setIconImage(new ImageIcon(InternalConfigStrings.getString("OpenLVLauncher.FrameIconPath")).getImage());

		//maximize to user specified percentage of screen
		//int percentWidth  = Integer.parseInt(InternalConfigStrings.getString("OpenLVLauncher.PercentageOfScreenWidth"));
		//int percentHeight = Integer.parseInt(InternalConfigStrings.getString("OpenLVLauncher.PercentageOfScreenHeight"));
		int percentWidth  = 500;
		int percentHeight = 175;

		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

		win.setBounds(0,0,(int)(percentWidth) ,(int)(percentHeight));


		final JFileChooser inFileChooser = new JFileChooser();

		//Set up browse for input image file section
		final JTextField inputFileField = new JTextField();
		inputFileField.setColumns(30);
		inputFileField.setText(InternalConfigStrings.getString("OpenLVLauncher.DefaultInputFile"));
		inputFileField.setToolTipText(InternalConfigStrings.getString("OpenLVLauncher.ToolTipInputFile"));

		final JButton browseInputButton = new JButton("Browse"); 

		inFileChooser.setFileFilter(new ImageFileFilter());
		//       inFileChooser.setCurrentDirectory(new File("c:\\Temp"));    //TMV

		/* Browse Input button click action */
		browseInputButton.addActionListener(new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
		{             
			inFileChooser.setMultiSelectionEnabled(true);      //for chunked/split image files
			//fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);   //only allow files
			int returnVal = inFileChooser.showDialog(parent,"Select File(s)"); 
			if (returnVal == JFileChooser.APPROVE_OPTION) 
		{
			File[] inFiles = inFileChooser.getSelectedFiles();
			boolean singleFile = false;

			if(inFiles.length == 1)      //just 1 file
			singleFile = true;

		try
		{
			if(singleFile)   //single image file selected
		{
			if(inFiles[0].isFile())
			inputFileField.setText(inFiles[0].getCanonicalPath());   //write the path in the text field
			else
				System.err.println("The input file you selected is not recognized as valid, please select another.");
		}
		else   //multiple files selected
		{
			StringBuffer fNameBuf = new StringBuffer();
			for(int i = 0; i < inFiles.length; i++)   //write file paths comma delimited to the file path field
			{
				if(inFiles[i].isFile())
				{
					fNameBuf.append(inFiles[i].getCanonicalPath());
					if( i != inFiles.length - 1)
						fNameBuf.append(", ");
					inputFileField.setText(inFiles[i].getCanonicalPath());   //write the path in the text field
				}
				else
					System.err.println("The input file you selected is not recognized as valid, please select another.");
			}
			inputFileField.setText(fNameBuf.toString());   //write the paths to the selected files in the text field
		}
		}
		catch(IOException ioe)
		{
			System.err.println("Problem selecting the chosen input file. Please select another: " + ioe.getMessage()); 
		} 
		} 
		else 
		{ /*select was cancelled by the user*/ }
		}
		});

		/* Radio Buttons for Choosing image file(s) or physical disk */
		final JRadioButton imageFileRadioButton    = new JRadioButton("Image File(s)",true); //default choice
		final JRadioButton physicalDiskRadioButton   = new JRadioButton("Physical Disk");         

		imageFileRadioButton.setActionCommand("ImageFile");      //set action commands so we can tell what button is selected later 
		physicalDiskRadioButton.setActionCommand("PhysicalDisk"); 

		imageFileRadioButton.setToolTipText(InternalConfigStrings.getString("OpenLVLauncher.ToolTipImageFile"));   //tool tip for option group
		physicalDiskRadioButton.setToolTipText(InternalConfigStrings.getString("OpenLVLauncher.ToolTipPhysicalDisk"));

		//Put the image vs disk option buttons into a mutually exclusive button group
		final ButtonGroup bootSourceType = new ButtonGroup();
		bootSourceType.add(imageFileRadioButton);
		bootSourceType.add(physicalDiskRadioButton);

		/*  boot source (bit for bit disk image or physical disk) group (Panel) */
		JPanel bootSourcePanel = new JPanel();
		bootSourcePanel.add(imageFileRadioButton);   //add the two buttons to the panel
		bootSourcePanel.add(physicalDiskRadioButton);   
		//bootSourcePanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createBevelBorder(2)));   

		/* Combo box for choosing the physical disk from which to boot*/
		final JComboBox physicalDeviceCombo = new JComboBox();   

		physicalDeviceCombo.setEditable(false);
		physicalDeviceCombo.setToolTipText(InternalConfigStrings.getString("OpenLVLauncher.ToolTipPhysicalDeviceSelection"));

		/* Physical Device Selection Label */
		final JLabel physicalDeviceSelectionLabel = new JLabel(InternalConfigStrings.getString("OpenLVLauncher.PhysicalDeviceSelectionLabel"));

		/* Physical Device Selection Group (Panel) */
		final JPanel physicalDeviceSelectionPanel = new JPanel();
		physicalDeviceSelectionPanel.setLayout(new BoxLayout(physicalDeviceSelectionPanel,BoxLayout.LINE_AXIS));
		physicalDeviceSelectionPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
		physicalDeviceSelectionPanel.add(physicalDeviceSelectionLabel);      //add label
		physicalDeviceSelectionPanel.add(Box.createRigidArea(new Dimension(5, 0)));
		physicalDeviceSelectionPanel.add(physicalDeviceCombo);      //add combo box

		final JPanel bootSourceCardPanel = new JPanel(new CardLayout());
		JPanel inTextAndButton = new JPanel();
		inTextAndButton.add(inputFileField);
		inTextAndButton.add(browseInputButton);
		bootSourceCardPanel.add(inTextAndButton, "ImageFile");
		bootSourceCardPanel.add(physicalDeviceSelectionPanel, "PhysicalDisk");


		/* Listener for enabling/disabling the appropriate source of the image input areas */
		ActionListener sourceModeListener = new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{   
				CardLayout cl = (CardLayout)(bootSourceCardPanel.getLayout());
				cl.show(bootSourceCardPanel, e.getActionCommand());

				//             if(e.getActionCommand().compareTo("ImageFile") == 0)   //user chose source as image file
				//             {
				//                //enable imagefile input box
				//                inputFileField.setVisible(true);
				//                browseInputButton.setVisible(true);
				//                //disable device selection combo
				//                physicalDeviceCombo.setVisible(false);
				//                physicalDeviceSelectionLabel.setVisible(false);
				//                
				//             }
				//             else   //user chose device selection
				//             
				//                //enable device selection combo
				//                physicalDeviceCombo.setVisible(true);
				//                physicalDeviceSelectionLabel.setVisible(true);
				//                //disable imagefile input box
				//                inputFileField.setVisible(false);
				//                browseInputButton.setVisible(false);

				if(e.getActionCommand().compareTo("PhysicalDisk") == 0)
				{
					// fill physical device combo with info on physical devices     
					PhysicalDiskInfo[] physicalDeviceItems = OpenLVLauncher.getPhysicalDeviceItems();

					String displayString;
					PhysicalDiskInfo currPDI = null;
					physicalDeviceCombo.removeAllItems();
					for(int i = 0; i < physicalDeviceItems.length; i++)
					{
						currPDI = physicalDeviceItems[i];
						displayString = currPDI.getModel() + " (" + Math.ceil(currPDI.getSize()/(OpenLVLauncher.BYTES_PER_GIG)) + " GB)";

						//                     if(currPDI.isRemovable())   //only add removable devices (to prevent user from selecting their currently booted partition which is dangerous)
						physicalDeviceCombo.addItem(new ComboBoxItem(displayString, currPDI.getDeviceName()));
					}

					if(physicalDeviceCombo.getItemCount() > 0)      //if any items were added to the combo box
						physicalDeviceCombo.setSelectedIndex(0);   //default value is the first value
					else
						physicalDeviceCombo.addItem(new ComboBoxItem("No Suitable Disks Detected", null));
				}
			}
		};

		imageFileRadioButton.addActionListener(sourceModeListener);
		physicalDiskRadioButton.addActionListener(sourceModeListener);

		    /* Browse For Input Location Group */
            JPanel browseInPanel = new JPanel();   //1 col, unlimited rows
            browseInPanel.setLayout(new BoxLayout(browseInPanel,BoxLayout.PAGE_AXIS));
            browseInPanel.add(bootSourcePanel);
            //       browseInPanel.add(inTextAndButton);
            //       browseInPanel.add(physicalDeviceSelectionPanel);
            browseInPanel.add(bootSourceCardPanel);


	 Container mainContainer = win.getContentPane();
            mainContainer.setLayout(new BoxLayout(mainContainer, BoxLayout.PAGE_AXIS));
	    
            mainContainer.add(browseInPanel);
        final  JButton    addDisk = new JButton("Add this Disk");

	ActionListener addDiskListener = new ActionListener()
	{
		public void actionPerformed(ActionEvent e)
		{
			try{
				System.err.println("clicked it!");
				 dd.setBootSource(bootSourceType.getSelection().getActionCommand());
				//final String[] files=	inputFileField.getText().trim().split("\\s*,\\s*");
				dd.setFiles(inputFileField.getText().trim().split("\\s*,\\s*"),parent,workingDir);
				//retVal = bootSource + ": " +  Arrays.toString(files);
				win.dispose();
				}
			catch(OpenLVException lve){
				System.err.println("error: " + lve.getMessage()); 
			}

		}
	};
	addDisk.addActionListener(addDiskListener);

	JPanel addDiskPanel = new JPanel();
	addDiskPanel.add(addDisk);
	mainContainer.add(addDiskPanel);

		win.setVisible(true);

	win.toFront();
	
	diskInfo = "hi";
	return dd;

	}

	/**
	 * inspector for displaytext datamember
	 * @return displayText string
	 */
	public String toString()
	{
		return displayText;
	}

	/**
	 * inspector for displaytext datamember
	 * @return displayText string
	 */
	public String getDiskInfo()
	{
		return diskInfo;
	}
	/**
	 * inspector for displaytext datamember
	 * @return displayText string
	 */
	public String getDisplayText()
	{
		return displayText;
	}

	/**
	 * mutator for displaytext datamember
	 * @param displayText the value to store in the private datamember
	 */
	public void setDisplayText(String displayText)
	{
		this.displayText = displayText;
	}

	/**
	 * inspector for underlyingValuedatamember
	 * @return displayText string
	 */
	public String getUnderlyingValue()
	{
		return underlyingValue;
	}

	/**
	 * mutator for underlyingValue datamember
	 * @param underlyingValue the value to store in the private datamember
	 */
	public void setUnderlyingValue(String underlyingValue)
	{
		this.underlyingValue = underlyingValue;
	}
}
