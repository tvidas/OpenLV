/*
   Copyright (C) 2006-2008 Carnegie Mellon University

   Tim Vidas <tvidas at gmail d0t com>


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
 * ZipFileMenu
 *
 * @author Tim Vidas
 * @version 0.7, Jan 2009
 */

public class ZipFileMenu extends JDialog
{
	private String displayText;			//text displayed in the combo box dropdown
	private String underlyingValue;		//value used internally for that displayed text
	private JFrame parent;
	public JPopupMenu popup;
	private String diskInfo;
	private String retVal = "";
	int theValue =0;
	private File workingDir;

	public ZipFileMenu(JFrame p, File wd)//OpenLVLauncher p)//String displayText, String underlyingValue)
	{
		parent = p;
		workingDir = wd;
	}
	public ZipData showDialog(){
		final ZipData zd = new ZipData();

		final JDialog win = new JDialog(parent);

		win.setTitle("Create Help File");
		win.setModal(true);

		//Set the frame's icon to an image loaded from a file.
		win.setIconImage(new ImageIcon(InternalConfigStrings.getString("OpenLVLauncher.FrameIconPath")).getImage());

		//maximize to user specified percentage of screen
		//int percentWidth  = Integer.parseInt(InternalConfigStrings.getString("OpenLVLauncher.PercentageOfScreenWidth"));
		//int percentHeight = Integer.parseInt(InternalConfigStrings.getString("OpenLVLauncher.PercentageOfScreenHeight"));
		int percentWidth  = 500;
		int percentHeight = 300;

		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

		win.setBounds(0,0,(int)(percentWidth) ,(int)(percentHeight));


		final JFileChooser outFileChooser = new JFileChooser();

		//Set up browse for output image file section
		final JTextField outputFileField = new JTextField();
		outputFileField.setColumns(30);
		outputFileField.setText(InternalConfigStrings.getString("OpenLVLauncher.DefaultHelpOutputDir"));
		outputFileField.setToolTipText(InternalConfigStrings.getString("OpenLVLauncher.ToolTipHelpOutputDir"));

		final JButton browseInputButton = new JButton("Browse"); 

		//outFileChooser.setFileFilter(new ImageFileFilter());
		//       outFileChooser.setCurrentDirectory(new File("c:\\Temp"));    //TMV

		/* Browse Input button click action */
		browseInputButton.addActionListener(new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
		{             
			//outFileChooser.setMultiSelectionEnabled(true);      //for chunked/split image files
			outFileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);   //only allow dirs
			int returnVal = outFileChooser.showDialog(parent,"Select Output Directory"); 
			if (returnVal == JFileChooser.APPROVE_OPTION) 
		{
			File outFile = outFileChooser.getSelectedFile();

		try
		{
			outputFileField.setText(outFile.getCanonicalPath());
		}
		catch(IOException ioe)
		{
			System.err.println("Problem selecting the chosen output Dir. Please select another: " + ioe.getMessage()); 
		} 
		} 
		else 
		{ /*select was cancelled by the user*/ }
		}
		});

//		/* Radio Buttons for Choosing image file(s) or physical disk */
//		final JRadioButton imageFileRadioButton    = new JRadioButton("Image File(s)",true); //default choice
//		final JRadioButton physicalDiskRadioButton   = new JRadioButton("Physical Disk");         

//		imageFileRadioButton.setActionCommand("ImageFile");      //set action commands so we can tell what button is selected later 
//		physicalDiskRadioButton.setActionCommand("PhysicalDisk"); 

//		imageFileRadioButton.setToolTipText(InternalConfigStrings.getString("OpenLVLauncher.ToolTipImageFile"));   //tool tip for option group
//		physicalDiskRadioButton.setToolTipText(InternalConfigStrings.getString("OpenLVLauncher.ToolTipPhysicalDisk"));

//		//Put the image vs disk option buttons into a mutually exclusive button group
//		final ButtonGroup outZipType = new ButtonGroup();
//		outZipType.add(imageFileRadioButton);
//		outZipType.add(physicalDiskRadioButton);

//		/*  boot source (bit for bit disk image or physical disk) group (Panel) */
//		JPanel outZipPanel = new JPanel();
//		outZipPanel.add(imageFileRadioButton);   //add the two buttons to the panel
//		outZipPanel.add(physicalDiskRadioButton);   
		//outZipPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createBevelBorder(2)));   

//		/* Combo box for choosing the physical disk from which to boot*/
//		final JComboBox physicalDeviceCombo = new JComboBox();   

//		physicalDeviceCombo.setEditable(false);
//		physicalDeviceCombo.setToolTipText(InternalConfigStrings.getString("OpenLVLauncher.ToolTipPhysicalDeviceSelection"));

//		/* Physical Device Selection Label */
//		final JLabel physicalDeviceSelectionLabel = new JLabel(InternalConfigStrings.getString("OpenLVLauncher.PhysicalDeviceSelectionLabel"));

//		/* Physical Device Selection Group (Panel) */
//		final JPanel physicalDeviceSelectionPanel = new JPanel();
//		physicalDeviceSelectionPanel.setLayout(new BoxLayout(physicalDeviceSelectionPanel,BoxLayout.LINE_AXIS));
//		physicalDeviceSelectionPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
//		physicalDeviceSelectionPanel.add(physicalDeviceSelectionLabel);      //add label
//		physicalDeviceSelectionPanel.add(Box.createRigidArea(new Dimension(5, 0)));
//		physicalDeviceSelectionPanel.add(physicalDeviceCombo);      //add combo box

		final JLabel helpMessageLabel = new JLabel("Help Message:");
		final JTextArea helpMessageArea = new JTextArea();
		helpMessageArea.setFont(new Font("Arial", Font.PLAIN, 12));
		helpMessageArea.setColumns(30);
		helpMessageArea.setRows(3);
		helpMessageArea.setLineWrap(true);
		helpMessageArea.setWrapStyleWord(true);
		helpMessageArea.insert(InternalConfigStrings.getString("OpenLVLauncher.DefaultHelpMessage"),0);
		helpMessageArea.setToolTipText(InternalConfigStrings.getString("OpenLVLauncher.ToolTipHelpMessage"));
		JPanel helpJPanel = new JPanel();
		helpJPanel.add(helpMessageLabel);
		helpJPanel.add(helpMessageArea);

		final JLabel contactNameLabel = new JLabel("Name:");
		final JTextField contactName = new JTextField();
		contactName.setColumns(30);
		contactName.setText(InternalConfigStrings.getString("OpenLVLauncher.DefaultHelpContactName"));
		contactName.setToolTipText(InternalConfigStrings.getString("OpenLVLauncher.ToolTipHelpContactName"));
		JPanel contactJPanel = new JPanel();
		contactJPanel.add(contactNameLabel);
		contactJPanel.add(contactName);

		final JLabel emailAddrLabel = new JLabel("Email:");
		final JTextField emailAddr = new JTextField();
		emailAddr.setColumns(30);
		emailAddr.setText(InternalConfigStrings.getString("OpenLVLauncher.DefaultHelpEmail"));
		emailAddr.setToolTipText(InternalConfigStrings.getString("OpenLVLauncher.ToolTipHelpEmail"));
		JPanel emailJPanel = new JPanel();
		emailJPanel.add(emailAddrLabel);
		emailJPanel.add(emailAddr);

		final JPanel outZipCardPanel = new JPanel(new CardLayout());
		JPanel inTextAndButton = new JPanel();
		inTextAndButton.add(outputFileField);
		inTextAndButton.add(browseInputButton);
		inTextAndButton.add(contactJPanel);
		inTextAndButton.add(emailJPanel);
		inTextAndButton.add(helpJPanel);
		outZipCardPanel.add(inTextAndButton, "ImageFile");
//		outZipCardPanel.add(physicalDeviceSelectionPanel, "PhysicalDisk");


		/* Listener for enabling/disabling the appropriate source of the image output areas */
		ActionListener sourceModeListener = new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{   
				CardLayout cl = (CardLayout)(outZipCardPanel.getLayout());
				cl.show(outZipCardPanel, e.getActionCommand());

				//             if(e.getActionCommand().compareTo("ImageFile") == 0)   //user chose source as image file
				//             {
				//                //enable imagefile output box
				                outputFileField.setVisible(true);
				                browseInputButton.setVisible(true);
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
				//                //disable imagefile output box
				//                outputFileField.setVisible(false);
				//                browseInputButton.setVisible(false);

			}
		};

//		imageFileRadioButton.addActionListener(sourceModeListener);
//		physicalDiskRadioButton.addActionListener(sourceModeListener);

		    /* Browse For Input Location Group */
            JPanel browseInPanel = new JPanel();   //1 col, unlimited rows
            browseInPanel.setLayout(new BoxLayout(browseInPanel,BoxLayout.PAGE_AXIS));
//            browseInPanel.add(outZipPanel);
            //       browseInPanel.add(inTextAndButton);
            //       browseInPanel.add(physicalDeviceSelectionPanel);
            browseInPanel.add(outZipCardPanel);


	 Container mainContainer = win.getContentPane();
            mainContainer.setLayout(new BoxLayout(mainContainer, BoxLayout.PAGE_AXIS));
	    
            mainContainer.add(browseInPanel);
        final  JButton    addZip = new JButton("Create Help Zip Package");

	ActionListener addZipListener = new ActionListener()
	{
		public void actionPerformed(ActionEvent e)
		{
			try{
				System.err.println("clicked it!");
				zd.setFilePath(outputFileField.getText().trim());
				zd.setContactEmail(emailAddr.getText().trim());
				zd.setContactName(contactName.getText().trim());
				zd.setHelpMessage(helpMessageArea.getText().trim());
				//retVal = outZip + ": " +  Arrays.toString(files);
				win.dispose();
				}
			catch(Exception lve){
				System.err.println("error: " + lve.getMessage()); 
			}

		}
	};
	addZip.addActionListener(addZipListener);

	JPanel addZipPanel = new JPanel();
	addZipPanel.add(addZip);
	mainContainer.add(addZipPanel);

		win.setVisible(true);

	win.toFront();
	
	diskInfo = "hi";
	return zd;

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
