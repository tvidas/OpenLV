package cert.forensics.liveview;
/*
 * Live view is a tool that takes a bit-by-bit image of a disk (such as those created with dd) 
 * and allows a user to boot it up in VMWare without modifying the image. All writes to disk 
 * are directed to a VMWare redo file which can be deleted (reverting all changes). 
 * 
 * Live View handles:
 * 	- Full disk images
 *  - Images of Partitions (builds a custom mbr for the partition)
 *  - Physical Hard Disks (with the aid of a usb or firewire writeblocking bridge)
 *  - Split images
 *  - VMware Workstation or Free VMware Server
 *  
 * It allows you to:
 *  - Specify the system time (set to time of seizure)
 *  - Continue working with your changes or start form scratch
 *  - Generate only the configuration files (user launches vmx manually) or automate the launch
 *  
 * Maybe some other stuff I forgot to add...
 *  
 * It also makes the necessary changes (installing an intel driver) to XP/2K systems to 
 * correct the 0x7B bluescreen issue when booting up a system installed on non-intel hardware
 * 
 * The Main Live View Class that launches the GUI, validates input, builds the config files, makes 
 * modifications to VM redo, launches the VM, etc. 
 * 
 * Author: 	Brian Kaplan
 * 			bfkaplan@andrew.cmu.edu
 * 
 * June 2006, Last Revised August 2006
 * Version 0.4
 * 
 * Copyright (C) 2006  Carnegie Mellon University
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 */

import java.io.*;

import javax.swing.*;

import cert.forensics.mbr.MasterBootRecord;
import java.awt.*;
import java.awt.event.*;

import java.text.*;
import java.util.ArrayList;
import java.util.Date;
import java.nio.channels.FileChannel;
import java.util.Arrays;

public class LiveViewLauncher 
{
	
	public  static final String 	endL = System.getProperty("line.separator");
	
	private static final JTextArea 	messageOutputArea = new JTextArea();	//program output area
	private static SwingWorker 		worker;	
	private static Process 			externalProc;	//external process(es) that are called throughout
	private static final Date 		now = new Date();	//current time/date
	
	private static final String 	dateFormat = InternalConfigStrings.getString("LiveViewLauncher.SystemTimeFormat");
	private static final String 	PARENT_DISK_SIZE_GB = "950";	//parent disk size for partitions (arbitrary large size -- was previously a user supplied value)
	private static		 String 	guestOSTypeText;	
	
	private static boolean 			startWasClicked = false;	//keeps track of whether launch button was clicked or not
	
	private static final String 	MOUNT_DRIVE_LETTER 	= getNextFreeDriveLetter('k');	//get next free drive letter for mounting	
	private static final int 		vmWareInstallType 	= isUsingVMWareServer();		//is the user using vmware server or vmware workstation
	
	private static final boolean	isVMWareServer 		= (vmWareInstallType == 1)? true : false;	//Server = 1, workstation = 2, -1 = error
	private static final String		VMWARE_VMRUN_PATH	= getVMWareVMRunPath(isVMWareServer);		//path to vmrun executable
	private static final String		VMWARE_MOUNT_PATH	= queryRegistryForVMMountPath();
	private static final double		JVM_MINIMUM_REQ		= 1.5;			//requires jvm 1.5 or higher
	private static final long 		BYTES_PER_GIG 		= 1073741824;	//2^30 bytes per gig
 
	public static void main(String args[])
    {    	   	
    	//JFrame.setDefaultLookAndFeelDecorated(true);	//Java style GUI
    	
    	//Get the native look and feel class name
        String nativeLF = UIManager.getSystemLookAndFeelClassName();
        
        //Set the look and feel
        try
        {
            UIManager.setLookAndFeel(nativeLF);
        } 
        catch (Exception e)
        {}
    	
    	/* The main Program Frame */
    	final JFrame frame = new JFrame(InternalConfigStrings.getString("LiveViewLauncher.TitleBarText")); 
    	frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);  	

    	//Set the frame's icon to an image loaded from a file.
    	frame.setIconImage(new ImageIcon(InternalConfigStrings.getString("LiveViewLauncher.FrameIconPath")).getImage());
    	
    	//maximize to user specified percentage of screen
    	int percentWidth  = Integer.parseInt(InternalConfigStrings.getString("LiveViewLauncher.PercentageOfScreenWidth"));
    	int percentHeight = Integer.parseInt(InternalConfigStrings.getString("LiveViewLauncher.PercentageOfScreenHeight"));
    	
    	Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    	
    	frame.setBounds(0,0,(int)(screenSize.width * percentWidth/100) ,(int)(screenSize.height * percentHeight/100));
    	
    	/* Program Output Area */
    	int outPtSize = Integer.parseInt(InternalConfigStrings.getString("LiveViewLauncher.OutputPointSize"));
    	messageOutputArea.setFont(new Font("Veranda", Font.PLAIN, outPtSize)); //fixed width font will look best
    	messageOutputArea.setLineWrap(true);
    	messageOutputArea.setWrapStyleWord(true);
    	
    	int outCols = Integer.parseInt(InternalConfigStrings.getString("LiveViewLauncher.OutputWindowWidth"));
    	int outRows = Integer.parseInt(InternalConfigStrings.getString("LiveViewLauncher.OutputWindowHeight"));
    	messageOutputArea.setColumns(outCols);
    	messageOutputArea.setRows(outRows);
    	messageOutputArea.setMargin(new Insets(5,5,0,0));
    	messageOutputArea.setEditable(false);
    	
    	JPanel outPanel = new JPanel(new BorderLayout());
    	outPanel.add(new JScrollPane(messageOutputArea), BorderLayout.CENTER);
     	outPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createBevelBorder(2),"Messages")); 
    	
    	/* RAM Field */
    	final JTextField sizeRamField = new JTextField();
    	sizeRamField.setColumns(7);	//set text field width
    	sizeRamField.setText(InternalConfigStrings.getString("LiveViewLauncher.DefaultRamSize"));
    	sizeRamField.setToolTipText(InternalConfigStrings.getString("LiveViewLauncher.ToolTipRamSize"));
    	
    	/* RAM Size Label */
    	final JLabel sizeRamLabel = new JLabel(InternalConfigStrings.getString("LiveViewLauncher.RamSizeLabel")); 

    	JPanel sizeRamPanel = new JPanel(new BorderLayout(0,0));
    	sizeRamPanel.add(sizeRamLabel);	//add label
    	sizeRamPanel.add(sizeRamField,BorderLayout.SOUTH);		//add text field
  	 	
       	/* System Time Field */
    	final JTextField systemTimeField = new JTextField();
    	systemTimeField.setColumns(20);	//set text field width
    	if(InternalConfigStrings.getString("LiveViewLauncher.DefaultSystemTime").compareTo("<NOW>") == 0)	//if user specifies <NOW> use current date/time as default sys time
    	{
    		DateFormat df = new SimpleDateFormat(dateFormat);
    		String formattedDate = null;

    		formattedDate = df.format(now);

    		systemTimeField.setText(formattedDate);
    	}
    	else
    		systemTimeField.setText(InternalConfigStrings.getString("LiveViewLauncher.DefaultSystemTime"));
    	
    	systemTimeField.setToolTipText(InternalConfigStrings.getString("LiveViewLauncher.ToolTipSystemTime"));
  
    	/* System Time Label */
    	final JLabel systemTimeLabel = new JLabel(InternalConfigStrings.getString("LiveViewLauncher.SystemTimeLabel")); 

    	/* System Time Group (Panel) */
    	JPanel systemTimePanel = new JPanel(new BorderLayout(0,0));
    	systemTimePanel.add(systemTimeLabel);	//add label
    	systemTimePanel.add(systemTimeField,BorderLayout.SOUTH);		//add text field
   
		
       	/* OS Selection Combo Box */
    	//map the user displayed OS choice vals to the vmware guest os values 
    	String[] guestOSVals = InternalConfigStrings.getString("LiveViewLauncher.GuestOSVals").split(","); //get the vmware guest OS values from properties file
    	String[] osVals = InternalConfigStrings.getString("LiveViewLauncher.OSChoices").split(","); //get the corresponding plain text os choices from properties file
    	
    	final JComboBox osSelectionCombo = new JComboBox();	
    	ComboBoxItem cbItem = null;
    	
    	//fill combo box with the OS values/guest os val pairs from above
    	for(int i = 0; i < guestOSVals.length && i < osVals.length; i++)
    	{
    		cbItem = new ComboBoxItem(osVals[i], guestOSVals[i]);
    		osSelectionCombo.addItem(cbItem);
    	}
    	osSelectionCombo.setSelectedIndex(0);	//default value is the first value
    	osSelectionCombo.setToolTipText(InternalConfigStrings.getString("LiveViewLauncher.ToolTipOSSelection"));
    	
    	/* OS Selection Label */
    	final JLabel osSelectionLabel = new JLabel(InternalConfigStrings.getString("LiveViewLauncher.OSSelectionLabel"));
    	
    	/* OS Selection Group (Panel) */
    	JPanel osSelectionPanel = new JPanel(new BorderLayout(0,0));
    	osSelectionPanel.add(osSelectionLabel);		//add label
    	osSelectionPanel.add(osSelectionCombo,BorderLayout.SOUTH);		//add combo box

    	
    	/* Radio Buttons for Generate Configs Only vs Generate and Launch VM */
    	final JRadioButton generateAndLaunch 	= new JRadioButton("Launch My Image",true); //default choice
    	final JRadioButton generateOnly  		= new JRadioButton("Generate Config Only");		   
		
		generateOnly.setActionCommand("GenerateOnly");		//set action commands so we can tell what button is selected later 
		generateAndLaunch.setActionCommand("GenerateAndLaunch"); 
		
		generateOnly.setToolTipText(InternalConfigStrings.getString("LiveViewLauncher.ToolTipGenerateOnly"));	//share tool tip for option group
		generateAndLaunch.setToolTipText(InternalConfigStrings.getString("LiveViewLauncher.ToolTipGenerateAndLaunch"));
		
		//Put the mode option buttons into a mutually exclusive button group
		final ButtonGroup modeGroup = new ButtonGroup();
		modeGroup.add(generateAndLaunch);
		modeGroup.add(generateOnly);

    	/*  mode (generate config files only or generate and launch vm) group (Panel) */
		JPanel modePanel = new JPanel();
		modePanel.add(generateAndLaunch);	//add the two buttons to the panel
		modePanel.add(generateOnly);	
		modePanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createBevelBorder(2),"What do you want to do?"));	
		
    	//input file and output dir selection
    	final JFileChooser outDirChooser = new JFileChooser();
    	final JFileChooser inFileChooser = new JFileChooser();

    	//Set up browse for output dir section
    	final JTextField directoryField = new JTextField();
    	directoryField.setColumns(30);
    	
    	if(InternalConfigStrings.getString("LiveViewLauncher.DefaultOutputDirectory").compareTo("<HOME>") == 0)	//if properties file specifies "<HOME>"
    		directoryField.setText(System.getProperty("user.home"));	//use user's home directory
    	else
    		directoryField.setText(InternalConfigStrings.getString("LiveViewLauncher.DefaultOutputDirectory"));	//use dir specified in properties file
    	
    	directoryField.setToolTipText(InternalConfigStrings.getString("LiveViewLauncher.ToolTipOutputDirectory"));
        
    	final JButton browseOutputButton = new JButton("Browse"); 

        /* Browse button click action */
    	browseOutputButton.addActionListener(new ActionListener()
    	{
    		public void actionPerformed(ActionEvent e)
    		{
    			outDirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);	//only allow directories
    			int returnVal = outDirChooser.showDialog(frame,"Select Directory"); 
                if (returnVal == JFileChooser.APPROVE_OPTION) 
                {
                    File dir = outDirChooser.getSelectedFile();
                	try
					{
                		directoryField.setText(dir.getCanonicalPath());	//write the path in the text field
   					}
                	catch(IOException ioe)
					{
                		postError("Problem selecting the chosen directory. Please select another: " + ioe.getMessage()); 
					} 
                } 
                else 
                { /*select was cancelled by the user*/   	
                }
    		}
    	});
    	
    	//Set up browse for input image file section
    	final JTextField inputFileField = new JTextField();
    	inputFileField.setColumns(30);
    	inputFileField.setText(InternalConfigStrings.getString("LiveViewLauncher.DefaultInputFile"));
    	inputFileField.setToolTipText(InternalConfigStrings.getString("LiveViewLauncher.ToolTipInputFile"));
        
    	final JButton browseInputButton = new JButton("Browse"); 

    	inFileChooser.setFileFilter(new ImageFileFilter());
    	
        /* Browse Input button click action */
    	browseInputButton.addActionListener(new ActionListener()
    	{
    		public void actionPerformed(ActionEvent e)
    		{    			
    			inFileChooser.setMultiSelectionEnabled(true);		//for chunked/split image files
    			//fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);	//only allow files
    			int returnVal = inFileChooser.showDialog(frame,"Select File(s)"); 
                if (returnVal == JFileChooser.APPROVE_OPTION) 
                {
                    File[] inFiles = inFileChooser.getSelectedFiles();
                    boolean singleFile = false;
                   
                    if(inFiles.length == 1)		//just 1 file
                    	singleFile = true;

                	try
					{
                		if(singleFile)	//single image file selected
                		{
                			if(inFiles[0].isFile())
                				inputFileField.setText(inFiles[0].getCanonicalPath());	//write the path in the text field
                			else
                				postError("The input file you selected is not recognized as valid, please select another.");
                		}
                		else	//multiple files selected
                		{
                			StringBuffer fNameBuf = new StringBuffer();
                			for(int i = 0; i < inFiles.length; i++)	//write file paths comma delimited to the file path field
                			{
                				if(inFiles[i].isFile())
                				{
                					fNameBuf.append(inFiles[i].getCanonicalPath());
                					if( i != inFiles.length - 1)
                						fNameBuf.append(", ");
                					inputFileField.setText(inFiles[i].getCanonicalPath());	//write the path in the text field
                				}
                				else
                					postError("The input file you selected is not recognized as valid, please select another.");
                			}
                			inputFileField.setText(fNameBuf.toString());	//write the paths to the selected files in the text field
                		}
   					}
                	catch(IOException ioe)
					{
                		postError("Problem selecting the chosen input file. Please select another: " + ioe.getMessage()); 
					} 
                } 
                else 
                { /*select was cancelled by the user*/ }
    		}
    	});
    	    	
    	/* Radio Buttons for Choosing image file(s) or physical disk */
    	final JRadioButton imageFileRadioButton 	= new JRadioButton("Image File(s)",true); //default choice
    	final JRadioButton physicalDiskRadioButton	= new JRadioButton("Physical Disk");		   
		
    	imageFileRadioButton.setActionCommand("ImageFile");		//set action commands so we can tell what button is selected later 
    	physicalDiskRadioButton.setActionCommand("PhysicalDisk"); 
		
    	imageFileRadioButton.setToolTipText(InternalConfigStrings.getString("LiveViewLauncher.ToolTipImageFile"));	//tool tip for option group
    	physicalDiskRadioButton.setToolTipText(InternalConfigStrings.getString("LiveViewLauncher.ToolTipPhysicalDisk"));
		
		//Put the image vs disk option buttons into a mutually exclusive button group
		final ButtonGroup bootSourceType = new ButtonGroup();
		bootSourceType.add(imageFileRadioButton);
		bootSourceType.add(physicalDiskRadioButton);

    	/*  boot source (bit for bit disk image or physical disk) group (Panel) */
		JPanel bootSourcePanel = new JPanel();
		bootSourcePanel.add(imageFileRadioButton);	//add the two buttons to the panel
		bootSourcePanel.add(physicalDiskRadioButton);	
		//bootSourcePanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createBevelBorder(2)));	

		/* Combo box for choosing the physical disk from which to boot*/
    	final JComboBox physicalDeviceCombo = new JComboBox();	
     	
    	physicalDeviceCombo.setEditable(false);
    	physicalDeviceCombo.setToolTipText(InternalConfigStrings.getString("LiveViewLauncher.ToolTipPhysicalDeviceSelection"));

    	/* Physical Device Selection Label */
    	final JLabel physicalDeviceSelectionLabel = new JLabel(InternalConfigStrings.getString("LiveViewLauncher.PhysicalDeviceSelectionLabel"));
    	
    	/* Physical Device Selection Group (Panel) */
    	final JPanel physicalDeviceSelectionPanel = new JPanel();
    	physicalDeviceSelectionPanel.add(physicalDeviceSelectionLabel);		//add label
    	physicalDeviceSelectionPanel.add(physicalDeviceCombo,BorderLayout.SOUTH);		//add combo box
    	
    	/* Listener for enabling/disabling the appropriate source of the image input areas */
       	ActionListener sourceModeListener = new ActionListener()
    	{
    		public void actionPerformed(ActionEvent e)
    		{   
    			if(e.getActionCommand().compareTo("ImageFile") == 0)	//user chose source as image file
    			{
    				//enable imagefile input box
    				inputFileField.setVisible(true);
    				browseInputButton.setVisible(true);
    				//disable device selection combo
    				physicalDeviceCombo.setVisible(false);
    				physicalDeviceSelectionLabel.setVisible(false);
    				
    			}
    			else	//user chose device selection
    			{
    				//enable device selection combo
    				physicalDeviceCombo.setVisible(true);
    				physicalDeviceSelectionLabel.setVisible(true);
    				//disable imagefile input box
    				inputFileField.setVisible(false);
    				browseInputButton.setVisible(false);
    				
        	    	// fill physical device combo with info on physical devices  	
        	    	PhysicalDiskInfo[] physicalDeviceItems = getPhysicalDeviceItems();
        	    	
        	    	String displayString;
        	    	PhysicalDiskInfo currPDI = null;
        	    	physicalDeviceCombo.removeAllItems();
        	    	for(int i = 0; i < physicalDeviceItems.length; i++)
        	    	{
        	    		currPDI = physicalDeviceItems[i];
        	    		displayString = currPDI.getModel() + " (" + Math.ceil(currPDI.getSize()/BYTES_PER_GIG) + " GB)";
        	    		
        	    		if(currPDI.isRemovable())	//only add removable devices (to prevent user from selecting their currently booted partition which is dangerous)
        	    			physicalDeviceCombo.addItem(new ComboBoxItem(displayString, currPDI.getDeviceName()));
        	    	}
        	 
        			if(physicalDeviceCombo.getItemCount() > 0)		//if any items were added to the combo box
        		    	physicalDeviceCombo.setSelectedIndex(0);	//default value is the first value
        			else
        				physicalDeviceCombo.addItem(new ComboBoxItem("No Removable Devices Detected", null));
       			}
    		}
    	};
    	
    	imageFileRadioButton.addActionListener(sourceModeListener);
    	physicalDiskRadioButton.addActionListener(sourceModeListener);
    	
    	//set the default source mode (choose image files, not physical device)
		inputFileField.setVisible(true);	//enable imagefile input box
		browseInputButton.setVisible(true);
		physicalDeviceCombo.setVisible(false);	//disable device selection combo
		physicalDeviceSelectionLabel.setVisible(false);
    	
    	/* Start Button */
    	final  JButton 	start 		= new JButton("Start");
    	start.setToolTipText(InternalConfigStrings.getString("LiveViewLauncher.ToolTipStartButton"));
    	final  JMenuItem startItem	= new JMenuItem("Start");	//the menu start button

    	/* Clear Button */
    	final JButton 	clear 		= new JButton("Clear");
    	clear.setToolTipText(InternalConfigStrings.getString("LiveViewLauncher.ToolTipClearButton"));
    	final JMenuItem clearItem 	= new JMenuItem("Clear"); 	//the menu start button
    	
		/* Check that the system is running Windows */
    	if (System.getProperty("os.name").toUpperCase().indexOf("WINDOWS") == -1)
    	{
	    	Object[] options = {"Okay"};	//button titles
		   	int answer = JOptionPane.showOptionDialog(frame, 
						"Sorry, Live View is Currently a Windows Only Application",
						"Detected A Non-Windows Operating System",
						JOptionPane.OK_OPTION,
						JOptionPane.ERROR_MESSAGE,
						null,     			//no custom icon
						options,  			//the titles of buttons
						options[0]); 		//default button title
	
		   	if(answer == JOptionPane.OK_OPTION)	//user clicked ok button
		   	{
		   		System.exit(1);
		   	}
    	}
    	
    	/* Check that the user has an up to date JVM >= global var JVM_MINIMUM_REQ */
    	String javaVersion = System.getProperty("java.version", "0.0");
    	String jvmBaseVersion = javaVersion.substring(0,javaVersion.indexOf(".") + 2);	//version string including one digit after the decimal ie 1.5
    	double jvmBaseVersionNum = Double.parseDouble(jvmBaseVersion);

    	if (jvmBaseVersionNum < JVM_MINIMUM_REQ)	//if JVM doesnt meet minimum requirements, alert user
    	{
	    	Object[] options = {"Okay"};	//button text
		   	int answer = JOptionPane.showOptionDialog(frame, 
						"Sorry, Live View Requires a More Recent Version of Java (" + JVM_MINIMUM_REQ + " or higher)" + endL + "Please Visit www.java.com/getjava to Download the Latest Version.",
						"Detected an Old Version of Java: " + javaVersion,
						JOptionPane.OK_OPTION,
						JOptionPane.ERROR_MESSAGE,
						null,     			//no custom icon
						options,  			//the titles of buttons
						options[0]); 		//default button title
	
		   	if(answer == JOptionPane.OK_OPTION)	//user clicked ok button
		   	{
		   		System.exit(1);		//close program
		   	}
    	}
    	
		/* Check that Some Version of VMWare is installed */
    	if (vmWareInstallType == -1)	//if no vmware install (workstation or server) detected
    	{
	    	Object[] options = {"Okay"};	//button titles
		   	int answer = JOptionPane.showOptionDialog(frame, 
						"VMware Workstation 5.5 or VMware Server 1.0 is required to run Live View" + endL + "Please visit www.vmware.com to download a copy and try again.",
						"No VMWare installation detected",
						JOptionPane.OK_OPTION,
						JOptionPane.ERROR_MESSAGE,
						null,     			//no custom icon
						options,  			//the titles of buttons
						options[0]); 		//default button title
	
		   	if(answer == JOptionPane.OK_OPTION)  //user clicked OK button
		   	{
		   		System.exit(1);		//exit program
		   	}
    	}
    	
		/* Check that Some VMWare Disk Mount Is Installed */
    	if (VMWARE_MOUNT_PATH == null)	//if no vmware-mount is detected
    	{
	    	Object[] options = {"Okay"};	//button titles
		   	int answer = JOptionPane.showOptionDialog(frame, 
						"The VMware Disk Mount Utility is required to run Live View" + endL + "Please visit www.vmware.com to download a copy and try again.",
						"No VMWare Disk Mount installation detected",
						JOptionPane.OK_OPTION,
						JOptionPane.ERROR_MESSAGE,
						null,     			//no custom icon
						options,  			//the titles of buttons
						options[0]); 		//default button title
	
		   	if(answer == JOptionPane.OK_OPTION)  //user clicked OK button
		   	{
		   		System.exit(1);		//exit program
		   	}
    	}
    	
    	/* Start button click action */
    	ActionListener startAListener = new ActionListener()
    	{
    		public void actionPerformed(ActionEvent e)
    		{
    			startWasClicked = true;
    		    			
   				final String sizeRamText 	 	= sizeRamField.getText().trim();		//user input ram string
   				final String systemTimeText		= systemTimeField.getText().trim();		//user input sys time string
   			
   				guestOSTypeText					= ((ComboBoxItem)osSelectionCombo.getSelectedItem()).getUnderlyingValue();
   				
   				final String 	mode				= modeGroup.getSelection().getActionCommand();	//generate | generate and launch
   				
   				final String	bootSource			= bootSourceType.getSelection().getActionCommand();	//ImageFile | PhysicalDisk
   				final String 	diskSize			= PARENT_DISK_SIZE_GB;			//arbitrarily large constant			
   				
   				long totalSectorsOnParentDisk = 0;			//sectors on disk image
   				
   				final boolean isPhysicalDisk = bootSource.equalsIgnoreCase("PhysicalDisk") ? true : false;	//is the user booting a physical disk or a dd image
   				
   				final String physicalDiskName;
   				final String physicalDiskModel;
   				if(isPhysicalDisk)
   				{
   					physicalDiskName = ((ComboBoxItem)physicalDeviceCombo.getSelectedItem()).getUnderlyingValue();	// \\.\PhsycialDiskX
   					physicalDiskModel = ((ComboBoxItem)physicalDeviceCombo.getSelectedItem()).getDisplayText();	//physical disk model string
   				}
   				else
   				{
   					physicalDiskName = null;
   					physicalDiskModel = null;
   				}
   				
   				final boolean isFullDisk;		//is the disk a full image (with mbr) or just bootable partition
   				   				
   				final MasterBootRecord mbr;		//MBR for the disk image being booted
   				
   				//start was clicked so disable it so it cannot be clicked repeatedly
   				Runnable updateStartButtonState = new Runnable() 
   				{
   				    public void run() 
   				    { 
   		    			start.setEnabled(false);		//disable start button
   		    			startItem.setEnabled(false);	//disable start menu item; 
   				    }
   				};
   				SwingUtilities.invokeLater(updateStartButtonState);
   				
   				DateFormat formatter = new SimpleDateFormat(dateFormat);
   				Date date = null;
			
   				try
   				{
	   				try
	   				{
	   					date = (Date)formatter.parse(systemTimeText);	//is user sys time input in proper format
	   				}
		   			catch(ParseException pe)
	   	   			{
		   				throw new LiveViewException("Invalid Date Format Entered: " + pe.getMessage() + endL + "Date Should Be In Format: " + dateFormat);
	   	   			}
					final long userSysTimeSince1970 = date.getTime() / 1000; 	//user specified seconds since January 1, 1970, 00:00:00 GMT
	   				final long currentSysTimeSince1970 = now.getTime() / 1000;	//current secs since Jan 1, 1970
	   				   				
	   				final String outDirVal	= directoryField.getText().trim();	//user specified output directory
	   				
	   				final String[] pathForInputFiles = inputFileField.getText().trim().split("\\s*,\\s*");	//extract array of input file paths
	   				
	   				sortChunkFileNamesByExtension(pathForInputFiles);	//sort the file extensions so they can be concatenated in order
	   				
	   				final boolean isChunked;
	   				if(pathForInputFiles.length == 1)
	   					isChunked = false;	//single file
	   				else
	   					isChunked = true;	//multiple chunked files	
	   				
	   				//final String inFilePath	= inputFileField.getText().trim();
	
	   				final File testDir = new File(outDirVal);	//test the output directory user input path to make sure it is an existing dir
	   				
	   				if(!testDir.isDirectory())
	   				{
	   					throw new LiveViewException("Invalid input value: " + outDirVal + " does not exist or is not a directory");  
	   				}
	   				
	   				//check the validity of the input strings
	   				try
					{	
	   					if(isPhysicalDisk && physicalDiskName == null)	//check if a valid physical disk was selected
	   						throw new LiveViewException("No USB or Firewire phyical disks could be detected. Please make sure they are properly attached to your machine and try again.");
	   					
	   					totalSectorsOnParentDisk = (long)(Float.parseFloat(diskSize) * BYTES_PER_GIG) / 512;
	   					
	   					int i;
	   					int lowerBoundRamSize = Integer.parseInt(InternalConfigStrings.getString("LiveViewLauncher.LowerBoundRamSizeMb"));
	   					int upperBoundRamSize = Integer.parseInt(InternalConfigStrings.getString("LiveViewLauncher.UpperBoundRamSizeMb"));   					
	   				
	   					//check if date is valid
	   					if(userSysTimeSince1970 < 0 || userSysTimeSince1970 > currentSysTimeSince1970)
	   					{
	   						throw new LiveViewException("Date specified must be between Jan 1, 00:00:00 GMT 1970 and " + now);
	   					}
	   					
	   					//check size of ram input
	   					i = Integer.parseInt(sizeRamText);
	   					if(i < lowerBoundRamSize || i > upperBoundRamSize) 
	   					{	
	   						throw new LiveViewException("Invalid Memory Size: " + i + " Size should be between " + lowerBoundRamSize + "Mb and " +  upperBoundRamSize + "Mb");  
	   					}
	   					
	   					if(i % 4 != 0)	//vmware requires ram to be multiple of 4
	   					{
	   						throw new LiveViewException("Invalid Memory Size: " + i + " Size must be a multiple of 4");
	   					}
					}
	   				catch(NumberFormatException nfe)
					{
	   					throw new LiveViewException("Invalid Input parameter(s) make sure you entered numerical values where applicable");
					}
	   				
	   				//for re-enabling the start button later on
	   				final Runnable enableStartButtonState = new Runnable() 
	   				{
	   				    public void run() 
	   				    { 
		        			start.setEnabled(true);		//re-enable start button
		        			startItem.setEnabled(true);	//re-enable start menu item	
		        		}
	   				};
	   				
	   				//check if the config file location for the vmrun executable is valid
	   				File tempCheckVMRun = new File(VMWARE_VMRUN_PATH);
	   				if(!tempCheckVMRun.exists())
	   				{
	   					throw new LiveViewException("Could Not Locate VMWare's vmrun executable on your system");
	   				}
	   				
	            	//make sure the mount drive letter is not already mounted from a previous run that did not properly clean up
	            	String mountDriveLetter = MOUNT_DRIVE_LETTER; 
	            	File testMountDriveLetter = new File(mountDriveLetter + ":");
	            	if(testMountDriveLetter.isDirectory())
	            	{
	            		postOutput("Warning: Detected " + testMountDriveLetter + " is already in use, attempting to unmount" + endL);
	            		boolean unmountSuccess = unmountSnapshot(mountDriveLetter, true);	//force unmount of disk image
	            		if(unmountSuccess)
	            		{
	            			postOutput("Successfully Cleaned Up, Continuing Normally" + endL);
	            		}
	            		else
	            		{
	            			throw new LiveViewException("Could not unmount " + testMountDriveLetter + ": Perhaps it is being used by another device.");
	            		}
	            	}
	            	
	            	if(isVMWareServer)
	            		postOutput("Detected VMWare Server Installation" + endL);
	            	else
	            		postOutput("Detected VMWare Workstation Installation" + endL);
	   				
	            	if(vmWareInstallType == -1)		//check if we found a valid vmware installation
	            	{
	            		throw new LiveViewException("Error Detecting VMWare Installation. Make sure you have either VMware Server or Workstation installed on your system.");
	            	}
	            	
	            	MasterBootRecord tmp512;
	            	File[] imgFiles = null;
   					File genericMBR = null; 
   					File customMBR = null;
   					
	            	if(!isPhysicalDisk)	//user chose to boot a disk image, not physical disk
	            	{
	            		//create array of files (chunks) for regular image it is just an array of length 1
	   					imgFiles = new File[pathForInputFiles.length];
	   					for(int i = 0; i < imgFiles.length; i++)
	   						imgFiles[i] = new File(pathForInputFiles[i].trim());
	   					
	   					for(int i = 0; i < imgFiles.length; i++)	//check if all input files exist
	   					{
	   						if(!imgFiles[i].exists())
	   						{
	   							throw new LiveViewException("The image file: " + imgFiles[i].getName() + " could not be found");
	   						}
	   					}
	   					
	   					//load first 512 bytes of image file supplied as an MBR to test its validity
	   					tmp512 = new MasterBootRecord(imgFiles[0]);	//first img file should contain mbr
	            	}
	            	else	//physical disk
	            	{
	            		String deviceName = physicalDiskName;
	            		int[] unsignedMBRBuffer = null;
	            		try
	            		{
	            			RandomAccessFile raFile = new RandomAccessFile(deviceName, "r");
	            		    byte[] mbrBuffer = new byte[512];	//signed mbr bytes
	            		    raFile.readFully(mbrBuffer);	
	            		    raFile.close();
	            		    unsignedMBRBuffer = new int[512];	//unsigned bytes from mbr
	            		    for(int i = 0; i < 512; i++)	//convert signed bytes to unsigned
	            		    {
	            		    	unsignedMBRBuffer[i] = mbrBuffer[i];
	            		    	if(mbrBuffer[i] < 0)
	            		    		unsignedMBRBuffer[i] = 256 + mbrBuffer[i];
	            		    }
	            		}
	            		catch(FileNotFoundException fnf)
	            		{
	            			throw new LiveViewException("Could not open physical device: " + deviceName + " " + fnf.getMessage());
	            		}
	            		catch(IOException ioe)
	            		{
	            			System.out.println("I/O problem reading physical device: " + deviceName + " " + ioe.getMessage());
	            		}
	            		
	            		tmp512 = new MasterBootRecord(unsignedMBRBuffer);
	            	}
   					
	            	final String imageName;
	            	if(isPhysicalDisk)
	            		imageName = physicalDiskModel;//physicalDiskName.substring(4,physicalDiskName.length());
	            	else
	            		imageName = imgFiles[0].getName();
	            	
   					/* check for the MBR identifying sequence 55AA in bytes 510-511 of MBR as a check that it is valid*/
   					if(tmp512.getMarker()[0] != 0x55 || tmp512.getMarker()[1] != 0xAA) 
   					{
   						throw new LiveViewException("The image: " + imageName + " does not appear to be a disk file or bootable partition"
   													+ endL
   													+ "Please make sure that the image file(s) you chose is a valid disk image");
   					}
   					else	//we almost certainly have an mbr or a partition (not a garbage file)
   					{}
					
   					//sanity check on user's image file selection
   					if(tmp512.isValidMBR())	//if mbr structure is a valid mbr (not first 512 bytes of a partition)
					{
						isFullDisk = true;	//full disk 
						postOutput("Detected full disk image" + endL);
					}
					else 
					{
						isFullDisk = false;	//partition only
						postOutput("Detected Partition Image" + endL);
					}
   				
   	   				String fsType = null;		//type of filesystem for this image (NTFS, FAT, etc)
   					if(!isFullDisk) //partition only
   					{
   						//read in and modify generic mbr
						long sizeOfPartition = 0;		//size of the partition in bytes
						for(int i = 0; i < imgFiles.length; i++)
						{
							sizeOfPartition += imgFiles[i].length();
						}

   						//we cannot autodetect the os for partition images (because there is no MBR)
						if(guestOSTypeText.equals("auto"))
						{
							throw new LiveViewException("Live View cannot auto detect the OS for partition images. Please select the image OS and try again.");
						}
						
						if(guestOSTypeText.equals("linux"))
						{
							throw new LiveViewException("Live View does not currently support linux partition images. If you have access to the full disk image, please select that and try again.");
						}
						
						//depending on the OS, choose the correct 'generic' mbr for the partition
						if(guestOSTypeText.equals("win98") || guestOSTypeText.equals("winMe"))	//win 98 or Me
							genericMBR = new File(InternalConfigStrings.getString("LiveViewLauncher.GenericMBRLocationW98Me"));
						else							//non-win98/Me  //TODO linux???
							genericMBR = new File(InternalConfigStrings.getString("LiveViewLauncher.GenericMBRLocation"));

						//mbr file based on the image file name with .mbr appended to it
						customMBR = new File(testDir.getAbsolutePath().trim() + 
													System.getProperty("file.separator") + 
													imgFiles[0].getName() + 
													".mbr");
						
						//note, this does not write the nt driver serial number (bytes 440-443 of mbr)
						//this is because we need the vmdk to mount the disk to check the registry for this
						//serial number but the vmdk is not created at this point. The serial number is added after the vmdk is created
						modifyGenericMBR(sizeOfPartition, 255, genericMBR, customMBR);		//creates customMBR
						mbr = new MasterBootRecord(customMBR);	//use modified generic mbr 				
   					}
   					else	//full disk
   					{
   						if(!isPhysicalDisk)
   							mbr = new MasterBootRecord(imgFiles[0]);	//use mbr from image
   						else
   							mbr = tmp512;
   						//check mbr flag for bootable partition and check if fat or ntfs
						if(mbr.getBootablePartition().isFAT())
							fsType = "FAT";
						else if(mbr.getBootablePartition().isNTFS())
							fsType = "NTFS";
						else
							fsType = "OTHER";
   					}
			
   					if(!guestOSTypeText.equals("auto"))	//if the user selected the OS
   					{
   						//sanity check on user's OS type selection
						if(OperatingSystem.isNTKernel(guestOSTypeText) && mbr.getBootablePartition().isNotWindowsBased())	//selected NT based sys, AND mbr has non windows FS
						{
							throw new LiveViewException("You have selected a Windows NT Based OS, but the bootable partition's filesystem does not appear to be compatible with NT Based Systems. Please select the correct OS for your disk image and try again.");
						}
   					}
   					
   					/* Create the output files (vmx and vmdk) in the output directory (same base name as the image file)*/
   	   				String outFileVMXName 			= imageName + ".vmx";
   	   				String outFileVMDKName 			= imageName + ".vmdk";

   	   				//build name for new file with full path to output file
   	   				final String fullOutVMXPath 	= testDir.getAbsolutePath().trim() + System.getProperty("file.separator") + outFileVMXName;		//vmx config
   	   				final String fullOutVMDKPath 	= testDir.getAbsolutePath().trim() + System.getProperty("file.separator") + outFileVMDKName;	//vmdk config
   	   				
   	   				File outVMXFile 				= new File(fullOutVMXPath);				//vmx file 
   	   				File outVMDKFile 				= new File(fullOutVMDKPath);			//vmdk file
  	   				
//	   				final int numExistingSnapshots = numSnapshots(fullOutVMXPath);
   	   				final int numExistingSnapshots;		//how many snapshots exist for this vmx already
   	   				if(snapshotExists(fullOutVMXPath))
   	   					numExistingSnapshots = 1;
   	   				else
   	   					numExistingSnapshots = 0;
 				
   	   				if(numExistingSnapshots == -1)	//error detecting snapshots
   	   				{
   	   					throw new LiveViewException("Problem Detecting Number of Snapshots. Check that the path to the vmrun executable is configured correctly");
   	   				}
   					
   	   				if(!isPhysicalDisk)
   	   				{
	   					/* check if any files in the image are not readonly */
	   					boolean writable = false;
	   					for(int i = 0; i < imgFiles.length; i++)
	   					{
	   						if(imgFiles[i].canWrite())
	   							writable = true;
	   					}
	   					
	   					if(writable)	//if any file is not read-only, prompt to make them read only
	   					{
	  						Object[] options = {"Yes", "No"};	//button titles
						   	int answer = JOptionPane.showOptionDialog(frame, 
									"Making your image file read-only will provide an extra layer of\n protection against accidental modification of evidence\n Would you like to make the image file(s) read-only?",
									"The image you have chosen is not read-only",
									JOptionPane.YES_NO_OPTION,
									JOptionPane.WARNING_MESSAGE,
									null,     			//no custom icon
									options,  			//the titles of buttons
									options[0]); 		//default button title
	
						   	if(answer == JOptionPane.YES_OPTION)	//user wants to make file(s) read-only
						   	{
						   		for(int i = 0; i < imgFiles.length; i++)	//set image files to read-only
						   			imgFiles[i].setReadOnly();
						   		postOutput("Making image file(s) read-only at user's request" + endL);
						   	}	   	
	  					}
   	   				}
   	   				
   	   				int answerContStartOver;		//user response to continue or start from scratch
   	   				final boolean startFromScratch;	
   	   				
   	   				//if image has been launched before, prompt user to continue working or start over
   	   				if(numExistingSnapshots == 1)	//found existing snapshot (meaning this image was previusly launched)
   	   				{
   	   					
  	   					//prompt user for "continue working" or "Start over"
   	   					Object[] options = {"Continue", "Start Over"};	//button titles
   	   					answerContStartOver = JOptionPane.showOptionDialog(frame, 
   	   							"It appears that this disk image has been launched previously. \n Would you like to continue working where you left off or start from scratch?",
   	   							"This image has been previously launched",
   	   							JOptionPane.YES_NO_OPTION,
   	   							JOptionPane.QUESTION_MESSAGE,
   	   							null,     			//no custom icon
   	   							options,  			//the titles of buttons
   	   							options[0]); 		//default button title

		   	   			
   	   					if(answerContStartOver == JOptionPane.NO_OPTION)	//user wants to start from scratch
   	   					{
   	   						String 	prefixOfCurrentImage = imageName;
   	   						File[] filesInDir = testDir.listFiles();
   	   						String fName;
   	   						postOutput("User Chose To Start From Scratch" + endL);
   	   						for(int i = 0; i < filesInDir.length; i++)	//for each file in chosen directory
   	   						{
   	   							fName = filesInDir[i].getName();
   	   							boolean dontDelete = false;
   	   							if(fName.startsWith(prefixOfCurrentImage) && !fName.endsWith(".mbr"))	//if prefix of file matches, delete it (dont delete .mbr which was created earlier) 
   	   							{
   	   								if(!isPhysicalDisk)	//images, not physical disk
   	   								{
	   	   								for(int n = 0; n < imgFiles.length; n++)	//for all of the input image files
	   	   								{
	   	   									if(imgFiles[n].getName().compareTo(fName) == 0)	//file name matches one of the image files itself (we dont want to delete those)
	   	   										dontDelete = true;
	   	   								}
   	   								}
   	   								if(!dontDelete)	//if current file in dir is not one of the image files
   	   								{
   	   									filesInDir[i].delete();	//delete it
   	   								}
   	   							}
   	   						}
   	   						postOutput("Cleaned up old files" + endL);
   	   					}   	
   	   				}
   	   				else
   	   					answerContStartOver = JOptionPane.NO_OPTION; //if no snapshots, the automatic choice is "start over"

   	   				if(answerContStartOver == JOptionPane.NO_OPTION)
   	   					startFromScratch = true;
   	   				else
   	   					startFromScratch = false;
   	   				
   	   				//check if the vmx output file already exists
   	   				if(!outVMXFile.exists())
   	   				{
   	   					try	//no, so create it
   						{  
   	   						outVMXFile.createNewFile();	//create the file
   	   					}
   	   					catch(IOException ioe)
   						{
   	   						throw new LiveViewException("Could not create file: " + outFileVMXName + " in " + outDirVal + ioe.getMessage());  
   						}
   	   				}
   	   				else if(!outVMXFile.canWrite()) //check if the file is writable
   	   				{
   	   					throw new LiveViewException(outFileVMXName + " in " + outDirVal + " is not writable."
   	   												+ endL
   	   												+ "Please make the file writable or choose a new one.");  
   	   				}
   	   				
   	   				//vmx file exists and is writable, so write it
   					PrintWriter vmxWriter = null;
   					try
   					{
   						postOutput("Generating vmx file..." + endL);
   						vmxWriter = new PrintWriter(new BufferedWriter( new FileWriter(outVMXFile) ));
   						vmxWriter.write("#Static Values" + endL);
   						vmxWriter.write("config.version = \"8\"" + endL);
   						vmxWriter.write("virtualHW.version = \"3\"" + endL);
   						vmxWriter.write("floppy0.present = \"FALSE\"" + endL);
   						vmxWriter.write("displayName=\"" + imageName + "\"" + endL);
   					
   						vmxWriter.write(endL);
   						
   						vmxWriter.write("#Drive Info" + endL);
   						vmxWriter.write("ide0:0.present = \"TRUE\"" + endL);
   					
   						if(numExistingSnapshots > 0 && answerContStartOver == JOptionPane.YES_OPTION)	//if snapshots exist already and the user wants to Continue with what they were working on, point the filename to the snapshot
   							vmxWriter.write("ide0:0.fileName = \"" + fullOutVMDKPath.substring(0,fullOutVMDKPath.length()-5).concat("-000001.vmdk") + "\"" + endL);
   						else							//otherwise point the filename to the regualar vmdk
   							vmxWriter.write("ide0:0.fileName = \"" + fullOutVMDKPath + "\"" + endL);
   						
   						vmxWriter.write("ide0:0.deviceType = \"disk\"" + endL);
   						vmxWriter.write("ide0:0.mode = \"persistent\"" + endL);
   						
   						vmxWriter.write("ide1:0.present = \"TRUE\"" + endL);
   						vmxWriter.write("ide1:0.fileName = \"auto detect\"" + endL);
   						vmxWriter.write("ide1:0.deviceType = \"cdrom-raw\"" + endL);
   						
   						vmxWriter.write(endL);
   						
   						vmxWriter.write("#User Specified" + endL);
   						vmxWriter.write("memsize=\"" + sizeRamText + "\"" + endL);
   						vmxWriter.write("rtc.starttime=\"" + userSysTimeSince1970 + "\"" + endL);
  						
   						if(!guestOSTypeText.equals("auto"))	//if the user chose an OS manually from dropdown (not auto detect)
   							vmxWriter.write("guestOS=\"" + guestOSTypeText + "\"" + endL);
   						
   						if(!isVMWareServer)	//disable snapshots on vmware workstation to prevent accidental modification of original image -- vmware server does not have the snapshot tree so only necessary for workstation
   							vmxWriter.write("snapshot.disabled = \"TRUE\"");
   					}
   					catch(IOException ioe)
   					{
   						postError("Error writing vmx file: " + outVMXFile.getAbsolutePath());
   					}
   					finally 
   					{
   						if (vmxWriter != null) 
   							vmxWriter.close();
   					}		
   	 
   	   				//check if the vmdk output file exists
   	   				if(!outVMDKFile.exists())
   	   				{
   	   					try	//no, so create the file
   						{  
   	   						outVMDKFile.createNewFile();	//create the file
   	   					}
   	   					catch(IOException ioe)
   						{
   	   						throw new LiveViewException("Could not create file: " + outFileVMDKName + " in " + outDirVal
   	   													+ endL
   	   													+ ioe.getMessage());  
   						}
   	   				}
   	   				else if(!outVMDKFile.canWrite()) //check if the file is writable
   	   				{
   	   					throw new LiveViewException(outFileVMDKName + " in " + outDirVal + " is not writable."
   	   												+ endL
   	   												+ "Please make the file writable or choose a new one.");  
   	   				}
   	   				
   	   				//vmdk file exists and is writable, so write it
   					PrintWriter vmdkWriter = null;
   					try
   					{
   						postOutput("Generating vmdk file..." + endL);
   						vmdkWriter = new PrintWriter(new BufferedWriter( new FileWriter(outVMDKFile)));
   						vmdkWriter.write("# Disk Descriptor File" + endL);
   						vmdkWriter.write("version=1" + endL);
   						vmdkWriter.write("CID=fffffffe" + endL);
   						vmdkWriter.write("parentCID=ffffffff" + endL);
   						
						if(!isPhysicalDisk)	//image file(s)
   							vmdkWriter.write("createType=\"monolithicFlat\"" + endL);
						else	//physical disk
							vmdkWriter.write("createType=\"fullDevice\"" + endL);
						
   						vmdkWriter.write(endL);
   						
   						vmdkWriter.write("# Extent description" + endL);
   						
   						long unallocatedSpace;
   						if(!isFullDisk)	//just a partition image
   						{
   							if(customMBR != null)
   							{
   								vmdkWriter.write("RW 63 FLAT \"" + customMBR + "\" 0" + endL);	//add reference to mbr so we can boot partition
   							}
   							else
   								throw new LiveViewException("Custom MBR not found");
   							
   							if(isChunked)	//chunked partition image
   							{
   								long sectorsInChunk;
   								for(int i = 0; i < imgFiles.length; i++)
   								{
   									sectorsInChunk = imgFiles[i].length() / 512;	//number of sectors in current chunk
   									vmdkWriter.write("RW " + (sectorsInChunk) + " FLAT " + "\"" + imgFiles[i].getCanonicalPath() + "\"" + " 0" + endL);	//one line for each chunk for extent description
   								}
   								
   								unallocatedSpace = totalSectorsOnParentDisk - mbr.totalSectorsFromPartitions() - 63;	
   							}
   							else	//not a chunked partition image
   							{
   								vmdkWriter.write("RW " + (mbr.totalSectorsFromPartitions()) + " FLAT " + "\"" + imgFiles[0] + "\"" + " 0" + endL);	//just need one extent line pointing to whole image file
   								unallocatedSpace = totalSectorsOnParentDisk - mbr.totalSectorsFromPartitions() - 63;	
   							}
   						}
   						else			//full disk image
   						{
   							if(isChunked)	//chunked full disk image
   							{
   								long sectorsInChunk = 0, totalSectors = 0;
   								for(int i = 0; i < imgFiles.length; i++)
   								{
   									sectorsInChunk = imgFiles[i].length() / 512;
   									totalSectors += sectorsInChunk;
   									vmdkWriter.write("RW " + (sectorsInChunk) + " FLAT " + "\"" + imgFiles[i].getCanonicalPath() + "\"" + " 0" + endL);	//one line describing each chunk for the extent description
   								}
   								unallocatedSpace = totalSectors - mbr.totalSectorsFromPartitions() - 63;
   							}
   							else	//not a chunked full disk image
   							{
   								if(isPhysicalDisk)
   								{
   									vmdkWriter.write("RW " + (mbr.totalSectorsFromPartitions() + 63) + " FLAT " + "\"" + physicalDiskName + "\"" + " 0" + endL);	//just need one extent line pointing to the physical disk
   								}
   								else	//full disk dd image
   								{
   	   	   	   						vmdkWriter.write("RW " + mbr.totalSectorsFromPartitions() + " FLAT " + "\"" + imgFiles[0] + "\"" + " 0" + endL);	//just need one extent line pointing to whole image file
   								}
   								unallocatedSpace = mbr.totalSectorsOnDiskFromFile() - mbr.totalSectorsFromPartitions() - mbr.getBootablePartition().getEndSector() + 63;	//add 63?
   							}
   						}
					
   						if(unallocatedSpace > 0)
   							vmdkWriter.write("RW " + unallocatedSpace + " ZERO" + endL);	//leftover 0'ed space

   						vmdkWriter.write(endL);
   						
   						vmdkWriter.write("#DDB - Disk Data Base" + endL);
   						vmdkWriter.write("ddb.adapterType = \"ide\"" + endL);
   						vmdkWriter.write("ddb.geometry.sectors = \"" + mbr.getBootablePartition().getEndSector() + "\"" + endL);
   						vmdkWriter.write("ddb.geometry.heads = \"" + mbr.getBootablePartition().getEndHead() + "\"" + endL);
   						vmdkWriter.write("ddb.geometry.cylinders = \"" + mbr.largestCylinderValOnDisk() + "\"" + endL);
   						vmdkWriter.write("ddb.virtualHWVersion = \"3\"" + endL);
   					}
   					catch(IOException ioe)
   					{
   						throw new LiveViewException("Error writing vmdk file: " + outVMDKFile.getAbsolutePath());
   					}
   					finally 
   					{
   						if (vmdkWriter != null) 
   							vmdkWriter.close();
   					}		
   					
   	   				final File custMBR = customMBR;	//for access inside swingworker -- should probably change this
   	   				final String fileSysType = fsType;	//for access inside swingworker
   	   				
   	   				//share cpu time with GUI so it remains responsive during time consuming processes
	   				worker = new SwingWorker() 
					{
		                public Object construct() 
		                {
		                	boolean prepWorked = true; //did preparation for vm launch work
		                	
		                	String mountDriveLetter = MOUNT_DRIVE_LETTER; 
		                	int bootablePartitionIndex = mbr.getBootablePartitionIndex();
		                	
                			OperatingSystem os = null;
		                	if(numExistingSnapshots == 0 || startFromScratch)	//no snapshots already created or user chose to start from scratch
		                	{
		                		//TODO prepare all bootable partitions for launch (check each of the four entries)
		                		
		                		//prepare the bootable partition for launch
	                			os = prepareVMForLaunch(fullOutVMXPath, fullOutVMDKPath, mountDriveLetter, 
	                									guestOSTypeText, fileSysType, isFullDisk, bootablePartitionIndex,
                										testDir.getAbsolutePath().trim(), imageName);
                				
                				if(os != null)
                				{
                					guestOSTypeText = os.getVmGuestOS();
                					postOutput("Bootable Partition " + bootablePartitionIndex + ": " + guestOSTypeText + " prepared for launch" + endL);
                				}
                				else
                				{
                					postError("Problem preparing partition" + bootablePartitionIndex + " for launch");
                					prepWorked = false;
                				}
		                	}	
		                	else if(numExistingSnapshots == 1)	//user wants to continue with existing snapshot
		                	{
		                		postOutput("Using Existing Snapshot" + endL);
		                	}
		                	else
		                	{
		                		postError("Existing Snapshot Detection Failure: " + numExistingSnapshots);
		                		prepWorked = false;
		                	}

		                	//get find first nt partition on the disk (if there is one)
	   	   					int ntPartitionIndex = bootablePartitionIndex;
		                	
		   	   				//write the nt drive serial number to the customized mbr from above
		   	   				if(!isFullDisk && OperatingSystem.isNTKernel(guestOSTypeText)) //dealing with just an NTKernel partition 
		   	   				{
		   	   					int[] ntDriveSerialNum = {0x00, 0x00, 0x00, 0x00}; //generic 4 byte serial for non nt systems (those done require serial numbers)
		   	   					
		   	   					if(ntPartitionIndex > 0)	//if os is nt based (but not Original NT)
		   	   					{
		   	   						String vmdkSnapshotLoc = fullOutVMDKPath.substring(0,fullOutVMDKPath.length()-5).concat("-000001.vmdk");	//xyz.vmdk -> xyz-000001.vmdk  (snapshot naming convention)
		   	   						
		   	   						if(startFromScratch)	//if user wants to start from scratch
		   	   							ntDriveSerialNum = getNTDriveSerialNum(vmdkSnapshotLoc, false, os, ntPartitionIndex);	//get the NT drive serial number
		   	   						else
		   	   							ntDriveSerialNum = getNTDriveSerialNum(vmdkSnapshotLoc, true, os, ntPartitionIndex);	//get the NT drive serial number
		   	   					}
		   	   					
		   	   					if(ntDriveSerialNum != null)	//if we got the 4 byte serial number, write it to the mbr
		   	   					{
		   	   						try 
				   	   			    {
					   	   		        RandomAccessFile raf = new RandomAccessFile(custMBR, "rw");
			
					   	   		        //serial number starts at byte 440, so skip to there and write the 4 byte serial
						   	 	        raf.seek(440);	
						   		        raf.write(ntDriveSerialNum[0]); 	
						   		        raf.seek(441);
						   		        raf.write(ntDriveSerialNum[1]); 
						   		        raf.seek(442);
						   		        raf.write(ntDriveSerialNum[2]); 
						   		        raf.seek(443);
						   		        raf.write(ntDriveSerialNum[3]);
						   		        
					   	   		        raf.close();
					   	   		        postOutput("Custom MBR For Partition Generated Successfully" + endL);
				   	   			    } 
				   	   			    catch (IOException ioe) 
				   	   			    {
				   	   			    	postError("I/O error while writing nt drive serial number to custom mbr: " + ioe.getMessage());
				   	   			    }
		   	   					}
		   	   					else	//error retrieving drive serial
		   	   						prepWorked = false;
			   	   	   		}
		   	   				
		   	   				if(mode.equals("GenerateAndLaunch")) //did the user choose to launch the vm as well
			   	   			{
			        			if(prepWorked)	//if VM Launch preparation worked
			        			{			          			
			        				if(!isVMWareServer)	//VMWare Workstation
			        				{
					        			boolean started = startVMProc(VMWARE_VMRUN_PATH, //vmrun
					        						"start", 
					        						fullOutVMXPath
													);				//launch the vm image process
					        			if(started)					        			
					        				postOutput("VMWare Workstation Launch Completed" + endL);
					        			else
					        				postError("VMWare Workstation Launch Failed");
			        				}
			        				else				//VMWare Server
			        				{			        							
			        					boolean consoleLaunched = startVMWareServerConsole(fullOutVMXPath);	//start the console so the vm GUI is visible (otherwise the vm runs in the background)
			        					
			        					if(consoleLaunched)	//console successfully opened
			        					{
			        						postOutput("VMWare Console Started" + endL);
						        			//start the image in the console
			        						boolean started = startVMProc(VMWARE_VMRUN_PATH, 		//vmrun
					        							"start", 
					        							fullOutVMXPath
														);				//launch the vm image process
					        				
			        						if(started)	
			        							postOutput("Image Sucessfully Launched" + endL);
			        						else
			        							postOutput("VMware Server Launch Failed");

			        					}
			        					else
			        						postOutput("VMware Server Console Could Not Be Started" + endL);
			        				}
			        			}
			        			else
			        				postOutput("VM Launch Failed" + endL);
		   	   				}
		   	   				else
	   	   						postOutput("The VMWare configuration files have been generated in your chosen output directory" + endL);
		   	   				
		   	   				SwingUtilities.invokeLater(enableStartButtonState); //re-enable start button
		   	   				
		   	   				return null;
		                }
					};
					worker.start();

   				}
   				catch(LiveViewException lve)		//catch the launch errors
   				{
   					postError(lve.getMessage());
   					postError("Image could not be launched in the VM."); 
   					
	   				//for re-enable the start buttons
	   				final Runnable reEnableStartState = new Runnable() 
	   				{
	   				    public void run() 
	   				    { 
		        			start.setEnabled(true);		//re-enable start button
		        			startItem.setEnabled(true);	//re-enable start menu item	
		        		}
	   				};
	   				SwingUtilities.invokeLater(reEnableStartState);
   				}
    		}
    	};
    	start.addActionListener(startAListener);

    	/* clear button click action */
    	ActionListener clearAListener = new ActionListener()
    	{
    		public void actionPerformed(ActionEvent e)
    		{
    	        Runnable doClearJTextArea = new Runnable() 
				{
		            public void run() 
		            {
		            	messageOutputArea.setText(""); 
		            }
		        };
		        SwingUtilities.invokeLater(doClearJTextArea);
    		}
    	};
    	clear.addActionListener(clearAListener);
    	
    	//when the program is closed, make sure to kill the external process
        frame.addWindowListener(new WindowAdapter() 
        {
            public void windowClosing(WindowEvent e) 
            {
            		stopProc();
            		cleanUp();
            		System.exit(0);
            }
        });
  	
    	/* Browse For Output Location Group */
    	JPanel browseOutPanel = new JPanel();
		browseOutPanel.add(directoryField);
		browseOutPanel.add(browseOutputButton);
    	browseOutPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createBevelBorder(2),InternalConfigStrings.getString("LiveViewLauncher.VMOutputDirBorderText"))); 
  
    	/* Browse For Input Location Group */
    	JPanel browseInPanel = new JPanel();	//1 col, unlimited rows
    	browseInPanel.setLayout(new BoxLayout(browseInPanel,BoxLayout.PAGE_AXIS));
    	JPanel inTextAndButton = new JPanel();
    	inTextAndButton.add(inputFileField);
    	inTextAndButton.add(browseInputButton);
    	browseInPanel.add(bootSourcePanel);
    	browseInPanel.add(inTextAndButton);
    	browseInPanel.add(physicalDeviceSelectionPanel);
    	browseInPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createBevelBorder(2),InternalConfigStrings.getString("LiveViewLauncher.ForensicImageBorderText"))); 
    	
    	/* Action Button Group */
    	JPanel actionPanel = new JPanel();
		actionPanel.add(start);
    	actionPanel.add(clear);
    	actionPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createBevelBorder(2),"Actions")); 
    	
    	
    	/* Group the label/textbox panels together into a larger input panel */
    	JPanel inputPanelTop = new JPanel(new GridLayout(0,2));	//2 cols, as many rows as needed
    	inputPanelTop.add(sizeRamPanel);
    	inputPanelTop.add(systemTimePanel);
    	inputPanelTop.add(osSelectionPanel);
    	
    	JPanel inputPanel = new JPanel(new BorderLayout(0,0));
    	inputPanel.add(inputPanelTop);		//all regular input

    	inputPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createBevelBorder(2),InternalConfigStrings.getString("LiveViewLauncher.InputParamBorderText"))); 
    	 	
    	Container mainContainer = frame.getContentPane();
    	mainContainer.setLayout(new BoxLayout(mainContainer, BoxLayout.PAGE_AXIS));
    	mainContainer.add(inputPanel);
    	mainContainer.add(browseInPanel);

    	JPanel optionPanel = new JPanel(new GridLayout(2,1)); //group action + browse(in/out) together
//    	optionPanel.add(browseInPanel);
    	optionPanel.add(browseOutPanel);
    	optionPanel.add(modePanel);
    	mainContainer.add(optionPanel);
    	
    	mainContainer.add(actionPanel);
    	mainContainer.add(outPanel);
    	
    	//build the menus
    	JMenuBar menuBar = new JMenuBar();		//create a menu bar to hold the menus
        JMenu fileMenu = new JMenu("File");		//file menu is the main menu
        fileMenu.setMnemonic(KeyEvent.VK_F);	//allow for alt-f to access file menu
        
        //set up the actions sub menu
        JMenu actionsSubMenu = new JMenu("Actions");
        startItem.addActionListener(startAListener);
        clearItem.addActionListener(clearAListener);
        actionsSubMenu.add(startItem);
        actionsSubMenu.add(clearItem);
                
    	fileMenu.add(actionsSubMenu);	//add the actions submenu to the file parent menu
    	
    	JMenuItem aboutMenuItem = new JMenuItem("About");
    	aboutMenuItem.addActionListener(new ActionListener()
    	{
    		public void actionPerformed(ActionEvent e)
    		{
    	        Runnable doShowAboutDialog = new Runnable() 
				{
		            public void run() 
		            {
		            	JOptionPane.showMessageDialog(frame,InternalConfigStrings.getString("LiveViewLauncher.AboutBoxText"),"About This Program",JOptionPane.INFORMATION_MESSAGE);
		            }
		        };
		        SwingUtilities.invokeLater(doShowAboutDialog);
    		}
    	});
    	fileMenu.add(aboutMenuItem);
    	
    	/* Help Menu Item */
    	JMenuItem helpMenuItem = new JMenuItem("Help");
    	helpMenuItem.addActionListener(new ActionListener()
    	{
    		public void actionPerformed(ActionEvent e)
    		{
    			if(!launchHelpDoc(InternalConfigStrings.getString("LiveViewLauncher.HelpDocLocation")))
    				postError("Unable To Launch Help Document. Try Opening it Manually From The Live View Installation Directory.");
    		}	
   		});
    	fileMenu.add(helpMenuItem);
    	
    	/* Separator */
    	fileMenu.addSeparator();	
    	
    	/* Exit Menu Item */
    	JMenuItem exitMenuItem = new JMenuItem("Exit");
    	exitMenuItem.addActionListener(new ActionListener()
    	{
		    public void actionPerformed(ActionEvent e)
		    {
		    	stopProc();		//stop the running process (if running)
		    	cleanUp();		//clean up by unloading reg/unmounting disk etc
		    	System.exit(1);	//exit program
		    }
		});

    	fileMenu.add(exitMenuItem);
    	
    	menuBar.add(fileMenu);			//add the file menu to the menu bar
    	
    	frame.setJMenuBar(menuBar);	//add the menu bar to the frame
    	frame.pack();
    	frame.setVisible(true);
    }

    /*
     *  Performs a variety of steps that need to be done to ensure that an image can be booted in vmware
     * 
     *	This operation must be performed to correct the known 0x7b blue screen error on Win2k and XP
     *	systems that were installed on a chipset that is incompatible with VMWare's Intel based 
     *	BX440 motherboard. A driver and registry changes to the critical device database in the 
     *  are made to permit booting. This is all done inside a vmware redo file so no changes are 
     *  actually made to the underlying disk image. 
     *  
     *  Returns the OperatingSystem instance for partition 'partitionIndex' in the image
     */
    private static OperatingSystem prepareVMForLaunch(	String vmxLoc, String vmdkLoc, String mountDriveLetter, 
    											String userChosenGuestOS, String fsType, boolean isFullDisk, int partitionIndex,
    											String outputDir, String baseFileName)
    {
    	boolean autoDetect = userChosenGuestOS.equals("auto");	//did user select auto detect os
    	
    	if(isVMWareServer)
    	{
    		if(addToVMServerConfigFile(vmxLoc))	//vmware server maintains a list of known virtual machines. We need to add the vmx to the list or it cannot be opened
    			postOutput("VMX added to VMWare Server Config" + endL);
    		else
    		{
    			postError("Failed to add vmx to VMWare Server Config File");
    			return null;
    			//return false;
    		}
    	}
    	
		//create snapshot so we can redirect all disk access to it
    	if(createSnapshot(vmxLoc))
    	{
    		postOutput("Snapshot Created" + endL);
    	}
    	else
    	{
    		postError("Snapshot Creation Failed");
    		return null;
    		//return false;
    	}
    	
    	String vmdkSnapshotLoc = vmdkLoc.substring(0,vmdkLoc.length()-5).concat("-000001.vmdk");	//xyz.vmdk -> xyz-000001.vmdk  (snapshot naming convention)
    	
    	String osProductName;
    	
    	//create os variable for the current image
    	OperatingSystem os = null;
    	
    	try
    	{
	    	if(autoDetect)
	    		os = new OperatingSystem(mountDriveLetter, vmdkSnapshotLoc, partitionIndex, fsType, "auto");	//creat os and auto detect
	    	else
	    		os = new OperatingSystem(mountDriveLetter, vmdkSnapshotLoc, partitionIndex, fsType, userChosenGuestOS);	//create os with user defined val
    	}
    	catch(LiveViewException lve)
    	{
			postError(lve.getMessage());
			cleanUp();
			return null;
			//return false;	
    	}

    	
    	String guestOSVal = null;
    	if(autoDetect)
    	{
	    	osProductName = os.getPublicOSName();//queryRegistryForOSName(softHiveLoc);
	    	if(!osProductName.equalsIgnoreCase("Win9xOrLinux"))	//if not windows 9x or linux
	    	{
	    		guestOSVal = os.getVmGuestOS();
	    		
	    		if(OperatingSystem.getBaseOS(os.getVmGuestOS()).equals("nt"))
	    		{
		    		//unmount the snapshot (because it was mounted earlier to detect NT and system dir)
			    	if(unmountSnapshot(mountDriveLetter, false))	//unmount the snapshot for image from local machine FS
			    	{
			    		postOutput("Snapshot Unmounted" + endL);
			    	}
			    	else
			    	{
			    		postError("Snapshot Unmount Failed");
			    		//return false;
			    		return null;
			    	}    
	    		}
	    	}
	    	else	//win 9x or Linux -- TODO rather than default to "other" try to figure out actual guest os val
	    	{
	    		guestOSVal = os.getVmGuestOS();//"other";
	    		osProductName = os.getPublicOSName();//"Windows 9x or Linux";
	    		
	    		//unmount the snapshot (because it was mounted earlier to detect win9x)
		    	if(unmountSnapshot(mountDriveLetter, false))	//unmount the snapshot for image from local machine FS
		    	{
		    		postOutput("Snapshot Unmounted" + endL);
		    	}
		    	else
		    	{
		    		postError("Snapshot Unmount Failed");
		    		//return false;
		    		return null;
		    	}    
	    	}
		    
			postOutput("Detected " + osProductName + " installation on image" + endL);
	
			if(guestOSVal == null)	//no close guest OS match could be found for OS name
			{
				postError("Unknown Operating System: " + osProductName + " Please manually choose the most similar OS from the dropdown and try again");
				//return false;
				return null;
			}   	
			
	    	//add osname to vmx
	    	DataOutputStream vmxOutStream = null;
			try
			{
				vmxOutStream = new DataOutputStream(new FileOutputStream(vmxLoc,true));
				vmxOutStream.writeBytes("guestOS=\"" + guestOSVal + "\"" + endL);		//append guestOS="<osname>" to vmx file
				vmxOutStream.flush();
				vmxOutStream.close();
				postOutput("Added guest OS to vmx file" + endL);
			}
			catch(IOException ioe)
			{
				postError("Error writing vmx file: " + vmxLoc);
				//return false;
				return null;
			}
    	}
    	else	//not auto detect os
    	{
    		guestOSVal = userChosenGuestOS;
    		osProductName = os.getPublicOSName();
    	}
    	
		//if osname chosen is Me,98 or any other that doesnt require pre boot prep
    	if(!autoDetect && !OperatingSystem.isNTKernel(guestOSVal) && !guestOSVal.equalsIgnoreCase("linux"))	//if not xp, 2k, 2k3, or linux
    	{
	  
	    	//nt bluescreens when mounted so as a *WORKAROUND* we do not mount nt partitions anymore
	    	
//	    	if(OperatingSystem.getBaseOS(userChosenGuestOS).compareTo("nt") == 0)
//	    	{
//		    	//original nt was previously mounted so unmount it here since no further processing is required
//		    	if(unmountSnapshot(mountDriveLetter, false))	//unmount the snapshot for image from local machine FS
//		    	{
//		    		postOutput("Snapshot Unmounted" + endL);
//		    	}
//		    	else
//		    	{
//		    		postError("Snapshot Unmount Failed");
//		    		//return false;
//		    		return null;
//		    	}    
//	    	}
	    	
    		return os;//return osName; //we are done - no further prep needed for these os'
    	}		

    	//handle Windows NT separately
    	boolean isOriginalNT = false;
    	if(OperatingSystem.getBaseOS(userChosenGuestOS).compareTo("nt") == 0)
    		isOriginalNT = true;
    	    	
    	boolean isXP2Kor2K3 = OperatingSystem.isNTKernel(os.getVmGuestOS());	//doesnt include original NT
    	
    	//TODO Strange problem when clear passwords is checked for NT, it bluescreens on boot -- without it is fine (maybe mount/unmount happens too fast?)
    	if(isXP2Kor2K3 /*|| isOriginalNT*/)	//if OS is NT kernel based or original NT (ie NT4.0)
    	{
	    	
	    	if(!isOriginalNT)
	    	{	
	    		//try to extract the intelide.sys driver from one of the driver cache cab files on the image
	    		if(extractDriver("intelide.sys", os, mountDriveLetter))
	    		{
	    			postOutput("Intel IDE Driver Ready" + endL);
	    		}
		    	else	//extracting the driver from the image failed, try to find it on the host OS
		    	{
		    		postOutput("Driver Extraction From Image Failed, Checking Local Filesystem" + endL);
		    		
			    	//copy intelide.sys driver to prevent 0x7b blue screen error on XP,2k,2003
			    	String driverFileLoc = InternalConfigStrings.getString("LiveViewLauncher.DriverFileLocation");
		    		
		    		if(copyDriver(driverFileLoc, os, mountDriveLetter))	
			    	{
			    		postOutput("Intel IDE Driver Ready" + endL);
			    	}
		    		else
		    		{
			    		postError("Adding Intel IDE Driver Failed.");
			    		postError("Make Sure You Have Selected The Correct OS From The Choices Above");
				    	unmountSnapshot(mountDriveLetter, true);			//force unmount of snapshot
				    	postOutput("Snapshot Unmounted" + endL);
				    	//return false;
				    	return null;
		    		}
		    	}
		
	    		String systemHiveLoc = null;
	    		String systemRoot = os.getSystemRoot();
	    		if(systemRoot != null)
	    			systemHiveLoc = systemRoot + "\\system32\\config\\system";
	    		else
	    		{
		    		postError("System Hive Load Failed: Could not extract system root directory");
		    		//return false;
		    		return null;
	    		}
	    		
		    	if(loadSystemHive(systemHiveLoc))	//load the image system hive into local system registry
		    	{
		    		postOutput("System Hive Loaded" + endL);
		    	}
		    	else
		    	{
		    		postError("System Hive Load Failed");
		    		//return false;
		    		return null;
		    	}
		    	
		    	//merge registry entries to loaded hive
		    	String mergeFileLoc = InternalConfigStrings.getString("LiveViewLauncher.MergeFileLocation");
		    	
		    	if(makeChangesToRegistry(mergeFileLoc))	
		    	{
		    		postOutput("Critical Device Database Updated" + endL);
		    	}
		    	else
		    	{
		    		postError("Critical Device Database Update Failed");
		    		//return false;
		    		return null;
		    	}
	    	}	//if not original NT
	    	
	    	if(isFullDisk)	//if we are dealing with a full disk
	    	{
	    		if(!isOriginalNT)
	    		{
		    		//unload system hive
			    	if(unloadHive("SYSTEM"))	//unload the image system hive from local machine' registry
			    	{
			    		postOutput("System Hive Unloaded" + endL);
			    	}
			    	else
			    	{
			    		postError("System Hive Unload Failed");
			    		//return false;
			    		return null;
			    	}
	    		}
		    	
		    	//unmount snapshot
		    	if(unmountSnapshot(mountDriveLetter, false))	//unmount the snapshot for image from local machine FS
		    	{
		    		postOutput("Snapshot Unmounted" + endL);
		    	}
		    	else
		    	{
		    		postError("Snapshot Unmount Failed");
		    		//return false;
		    		return null;
		    	}
	    	}
	    	else	//partition only, so keep mounted snapshot open -- other things need to be done for partitions later
	    		postOutput("Keeping mounted snapshot open and registry loaded for partition" + endL);
    	}
    	else
    	{
    		//postOutput("OS is not NT Based" + endL);
    	}
    	
    	//return true;//return osName;
    	return os;
    }
    
    /*
     * Creates a snapshot for a virtual machine to which we can direct all disk
     * modifications leaving the original image file untouched. 
     * 
     * Snapshots are created differently depending on whether vmware server or workstation
     * is being used. 
     */
    private static boolean createSnapshot(String vmxLoc)
    {
    	String[] cmd;
    	
    	if(!isVMWareServer)		//VMWare Workstation
    	{
    		cmd = new String[4];
	    	cmd[0] = VMWARE_VMRUN_PATH;		//vmrun
	    	cmd[1] = "snapshot";	
	    	cmd[2] = vmxLoc;
	    	cmd[3] = "Original" + System.currentTimeMillis();		//unique snapshot name
    	}
    	else					//VMWare Server
    	{
    		cmd = new String[3];
	    	cmd[0] = VMWARE_VMRUN_PATH;		//vmrun
	    	cmd[1] = "snapshot";	
	    	cmd[2] = vmxLoc;
    	}
    	
    	String stdOut = callExternalProcess(cmd);
    	if(stdOut == null)
    		return false;
    	return true;
    }


    
    /*
     * Calls an external process and posts the stderr messages to the output window
     * 
     * Returns:	the output buffer from the command
     * 			null - failure
     */
    public static String callExternalProcess(String[] cmd)
    {
    	try
    	{
    		externalProc = Runtime.getRuntime().exec(cmd);
    		
    		//output messages
			ProcessStreamProcessor stdOutReader = new ProcessStreamProcessor(externalProc.getInputStream());
			//error messages
			ProcessStreamProcessor stdErrReader = new ProcessStreamProcessor(externalProc.getErrorStream());
			
			//start the threads that empty the output/error buffers
			stdOutReader.start();
			stdErrReader.start();

			//workaround -- wmic call hangs without it - ???
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(externalProc.getOutputStream()));
			bw.flush();
			bw.close();
			
			//check for exit val - ie any errors
			int exitVal = externalProc.waitFor();
			
			String errorMsg = stdErrReader.getReturnText();
			if(errorMsg.trim().length() > 0)
				postError(errorMsg);
    		
    		if(exitVal == 0)	//no error
    			return stdOutReader.getReturnText();
	    } 
	    catch (InterruptedException e) 
		{
	    	stopProc();
		    postError("The command: " + Arrays.toString(cmd) + " has been interrupted");  
	    }
	    catch ( IOException ioe)
		{
	    	stopProc();
	    	postError("I/O Error occurred while executing: " + Arrays.toString(cmd));  
	    	postError(ioe.getMessage());
		}
	    return null;
    }
    
    /*
     * Mounts a vmware snapshot (*.vmdk) on the local disk so that it can be modified as if it
     * were another mounted filesystem on the local machine. 
     */
    public static boolean mountSnapshot(String driveLetter, String snapshotVMDKLoc, int partition)
    {
    	String[] cmd = new String[4];
    	cmd[0] = VMWARE_MOUNT_PATH;//InternalConfigStrings.getString("LiveViewLauncher.VMMountExecutableLocation");		//vmware-mount
    	cmd[1] = "/v:" + partition;
    	cmd[2] = driveLetter + ":";	
    	cmd[3] = snapshotVMDKLoc;

    	String stdOut = callExternalProcess(cmd);
    	if(stdOut == null)
    		return false;
    	return true;
    			
    }
   
    /*
     * Loads the system hive (of the image) in the local system's registry under the branch:
     * HKLM\NEWSYSTEM
     * This allows us to query/modify the system hive of the image as if it was part of the local
     * system's registry (as opposed to writing an offline registry hive editor)
     */
    private static boolean loadSystemHive(String systemHiveLoc)
    {
    	//check if the system hive actually exists
    	File systemHiveFile = new File (systemHiveLoc);
    	if(!systemHiveFile.exists())
    	{
    		postError("System hive file could not be found on disk image"); 
    		return false;
    	}
    	
      	String[] cmd = new String[4];
    	cmd[0] = "reg";
    	cmd[1] = "load";	
    	cmd[2] = "HKLM\\NEWSYSTEM";
    	cmd[3] = systemHiveLoc;
    	
    	String stdOut = callExternalProcess(cmd);
    	if(stdOut == null)
    		return false;
    	return true;
    }
    
    /*
     * Loads the software hive (of the image) in the local system's registry under the branch:
     * HKLM\NEWSOFTWARE
     * This allows us to query/modify the system hive of the image as if it was part of the local
     * system's registry (as opposed to writing an offline registry hive editor)
     */
    public static boolean loadSoftwareHive(String softwareHiveLoc)
    {
    	//check if the system hive actually exists
    	File softwareHiveFile = new File (softwareHiveLoc);
    	if(!softwareHiveFile.exists())
    	{
    		postError("System hive file could not be found on disk image"); 
    		return false;
    	}
    	
      	String[] cmd = new String[4];
    	cmd[0] = "reg";
    	cmd[1] = "load";	
    	cmd[2] = "HKLM\\NEWSOFTWARE";
    	cmd[3] = softwareHiveLoc;
    	
    	String stdOut = callExternalProcess(cmd);
    	if(stdOut == null)
    		return false;
    	return true;
    }
    
    /*
     * Applies the contents of the merge file (*.reg) to the registry to make appropriate changes
     * to the critical device database so that the intelide.sys driver is loaded on boot to prevent
     * the 0x7b bluescreen boot errors. 
     * 
     * Precondition: system hive from image is already loaded into local system registry under HKLM\NEWSYSTEM
     */
    private static boolean makeChangesToRegistry(String mergeFileLoc)
    {   	
      	String[] cmd = new String[3];
    	cmd[0] = "regedit";
    	cmd[1] = "/s";	
    	cmd[2] = mergeFileLoc;
    	
    	String stdOut = callExternalProcess(cmd);
    	if(stdOut == null)
    		return false;
    	return true;
    }
    
    /*
     * Extracts the specified driver from the specified .cab to the appropriate drivers directory 
     * based on the OS passed in. The intelide.sys driver is necessary for overcoming the 0x7B blue 
     * screen error that occurs when booting a system on intel hardware (emulated by vmware) when it 
     * was not originally installed on different hardware. 
     */
    private static boolean extractDriver(String driverNameToExtract, OperatingSystem os, String destDriveLetter)
    {
    	
    	String driverDestinationLoc = null;
    	String systemRootDir = os.getSystemRoot();
    	if(systemRootDir != null)
    		driverDestinationLoc = os.getSystemRoot() + "\\system32\\drivers";
    	else 
    		return false; 	//unhandled os selected

    	//test if the intelide.sys driver is already present
		File f = new File(driverDestinationLoc + "\\" + driverNameToExtract);
		if(f.exists())
		{
			postOutput("Intel IDE Driver Already Exists On The System, Skipping Extraction" + endL);
			return true;
		}
    	
    	String driverCabLoc = null;
    	String cabPrefix = systemRootDir + "\\Driver Cache\\i386";
    	
    	File cabFile = null;
    	boolean foundCab = false;
    	for(int i = 0; i < 10 && !foundCab; i++)	//check spX.cab where X is the service pack number
    	{
    		driverCabLoc = cabPrefix + "\\sp" + i + ".cab";
    		cabFile = new File(driverCabLoc);
    		if(cabFile.exists())
    			foundCab = true;
    	}
    	if(!foundCab)		//no spX.cab files found, check driver.cab
    	{
    		driverCabLoc = cabPrefix + "\\driver.cab";
    		cabFile = new File(driverCabLoc);
    		if(cabFile.exists())
    			foundCab = true;
    	}
    	
    	if(!foundCab)	//no spX.cab or driver.cab files found (error)
    	{
    		postError("Could not locate intelide.sys driver in cab file: " + cabFile.getName() + " on system");
    		return false;
    	}
    	
    	//extract driver to drivers directory (from the cab file we found)
      	String[] cmd = new String[4];
    	cmd[0] = "expand";
    	cmd[1] = driverCabLoc;	
    	cmd[2] = "-f:" + driverNameToExtract;
    	cmd[3] = driverDestinationLoc;
    	
    	String stdOut = callExternalProcess(cmd);
    	if(stdOut == null)
    		return false;
    	return true;
    }
    
    /*
     * Copies the specified driver to the appropriate drivers directory based on the OS passed in
     * The intelide.sys driver is necessary for overcoming the 0x7B blue screen error that occurs 
     * when booting a system on intel hardware (emulated by vmware) when it was not originally installed
     * on different hardware. 
     */
    private static boolean copyDriver(String driverSourceLoc, OperatingSystem os, String destDriveLetter)
    {
    	String driverDestinationLoc = null;
    	
//    	String baseOS = getBaseOS(OS);
    	
    	String systemRootDir = os.getSystemRoot();
    	if(systemRootDir != null)
    		driverDestinationLoc = os.getSystemRoot() + "\\system32\\drivers";
    	else 
    		return false; 	//unhandled os selected

    	try 	//copy intelde.sys driver to drivers directory so it can be booted in vmware's intel based vm
        {
    		String driverName = "\\intelide.sys";
    		File f = new File(driverDestinationLoc + driverName);
    		if(!f.exists())
    		{
    			//create channel on the source
	            FileChannel srcChannel = new FileInputStream(driverSourceLoc).getChannel();
	        
	            //create channel on the destination
	            FileChannel dstChannel = new FileOutputStream(driverDestinationLoc + driverName).getChannel();
	        
	            //copy file contents from source to destination
	            dstChannel.transferFrom(srcChannel, 0, srcChannel.size());
	        
	            //close the channels
	            srcChannel.close();
	            dstChannel.close();
    		}
    		else
    			postOutput("Intel IDE Driver Already Exists On The System" + endL);
	            
	        return true;
        } 
        catch (IOException ioe) 
        {
	    	postError("I/O Error occurred while copying driver");  
	    	postError(ioe.getMessage());
        }
        return false;
    }
    
    /*
     * Unloads the system hive of the image from the local system's registry 
     * 
     * Precondition:
     * This assumes that the image's system/software hive was previously loaded under HKLM\NEWSYSTEM
     * or HKLM\NEWSOFTWARE
     * 
     * Input: hiveType - either SYSTEM or SOFTWARE
     */
    public static boolean unloadHive(String hiveType)
    {
      	String[] cmd = new String[3];
    	cmd[0] = "reg";
    	cmd[1] = "unload";	
    	cmd[2] = "HKLM\\NEW" + hiveType;
    	
    	String stdOut = callExternalProcess(cmd);
    	if(stdOut == null)
    		return false;
    	return true;
    }
    
    /* 
     * VMWare server maintains a list of vmx files it knows about (presumably as some sort of sanity check). This
     * method adds the vmx being launched to that list so that vmware server will permit a snapshot to be created
     */
    private static boolean addToVMServerConfigFile(String vmxPath)
    {
        BufferedWriter bw = null;
        String pathToVMList = System.getenv("ALLUSERSPROFILE") + InternalConfigStrings.getString("LiveViewLauncher.VMWareServerVMListLocation");
        String stringToAdd = "config " +  "\"" + vmxPath + "\"";
        
        //check if the entry is already there
        try 
        {
        	FileInputStream fin =  new FileInputStream(pathToVMList);
            BufferedReader bufReader = new BufferedReader(new InputStreamReader(fin));

        	String line;
        	while ((line = bufReader.readLine()) != null) 
        	{  
        		if(line.equals(stringToAdd))
        			return true;				//vmx is already in the config file
        	}
        }
        catch (Exception e) 
        {
        	postError("Could not read the VMWare Server vm-list file");
        	return false;	//error reading file
        }
        
    	//add the vmx to the config file (since it was not already found in there)
		try
		{
			bw = new BufferedWriter(new FileWriter(pathToVMList, true));
			bw.newLine();
			bw.write(stringToAdd);
			bw.newLine();
			bw.flush();
		}
		catch (IOException ioe)
		{
			postError("Could not find the path to the VMWare Server vm-list file");
			return false;
		}
		finally
		{ // always close the file
			if (bw != null)
			{
				try
				{
					bw.close();
				}
				catch (IOException io)
				{
					return false;
				}
			}
		} 
		return true;
    }
    
    /*
	 * Opens the specified page in the defualt browser Used for launching the
	 * help doc from the help menu item
	 */
    private static boolean launchHelpDoc(String url)
    {
      	String[] cmd = new String[3];
    	cmd[0] = "rundll32";
    	cmd[1] = "url.dll,FileProtocolHandler";
    	cmd[2] = url;	
    	
    	String stdOut = callExternalProcess(cmd);
    	if(stdOut == null)
    		return false;
    	return true;
    }
    
    /*
     * Unmounts a snapshot from local disk
     * 
     * force: 	true:  foribly unmount
     * 			false: gracefully unmount 
     * 
     * driveLetter:		the drive letter associated with the snapshot to unmount
     * 
     */
    private static boolean unmountSnapshot(String driveLetter, boolean force)
    {
    	String[] cmd = new String[3];
    	cmd[0] = VMWARE_MOUNT_PATH;//InternalConfigStrings.getString("LiveViewLauncher.VMMountExecutableLocation"); //vmware-mount
    	cmd[1] = driveLetter + ":";
    	
    	if(force)
    		cmd[2] = "/f";	//forcibly unmount
    	else
    		cmd[2] = "/d";	//gracefully unmount
    	
    	String stdOut = callExternalProcess(cmd);
    	if(stdOut == null)
    		return false;
    	return true;
    }
    
    /*
     * Loads up vmware and starts the virtual machine associated with the vmx 
     * This method is only currently used for starting a VM with VMWare Workstation (not server) 
     */
	private static boolean startVMProc(String progName, String command, String vmxFilePath)
	{
     
        final String[] cmd = new String[3];	//the command to execute

	    cmd[0]  = progName;		//vmrun
	    cmd[1]  = command;		//start
	    cmd[2]	= vmxFilePath;	//<image name>.vmx

        postOutput("Attempting to Launch Forensic Image in Virtual Machine" + endL);
        postOutput("Please Wait..." + endL);
           
        String stdOut = callExternalProcess(cmd);
        if(stdOut == null)
        	return false;
        return true;
	}
	
	/*
	 * 	Starts the vmware server console in preparation for launching the image. 
	 *  Note: I used rundll32 here because vmware.exe directly did not seem to return
	 *  an exit value until the console is closed.  
	 */
    private static boolean startVMWareServerConsole(String vmxPath)
    {
      	String[] cmd = new String[3];
    	cmd[0] = "rundll32";			
    	cmd[1] = "url.dll,FileProtocolHandler";
    	cmd[2] = vmxPath;	
    	
    	String stdOut = callExternalProcess(cmd);
    	if(stdOut == null)
    		return false;
    	return true;
    }
    
    /*
     * Utility function that determines whether a snapshot is associted with the vm
     * 
     * true = a snapshot is associated with the vm
     * false = no snapshot associated with vm
     */
	private static boolean snapshotExists(String pathToVMXFile)
	{
		String snapshotPath = pathToVMXFile.substring(0,pathToVMXFile.length()-4).concat("-000001.vmdk");	//standard naminc convention for snapshots
		File testFile = new File(snapshotPath);	
		return testFile.exists();	//does a file by that name exist
	}
	
	/*
	 * Utility function that returns the number of snapshots associated with 
	 * a particular vm.
	 * 
	 * Currently Unused -- because listSnapshots does not appear to work properly with VMware Server
	 * 
	 * 1  = 1 snapshot
	 * 0  = no snapshots
	 * -1 = error detecting snapshots
	 */
	private static int numSnapshots(String pathToVMXFile)
	{
	   	String[] cmd = new String[3];
    	cmd[0] = VMWARE_VMRUN_PATH;		//vmrun
    	cmd[1] = "listSnapshots";	
    	cmd[2] = pathToVMXFile;
      
    	int numSnapshots = -1;
    	
    	File vmx = new File(pathToVMXFile);
    	if(!vmx.exists())			//if vmx file does not even exist, numSnapshots is zero
    		return 0;

    	String stdOut = callExternalProcess(cmd);
    	
    	if(stdOut == null)
    		numSnapshots = -1;
    	else
    	{
            String[] lines = stdOut.split(System.getProperty("line.separator"));
     	    for(int i = 0; i < lines.length; i++)//for every line in the output
     	    {
     	    	if(lines[i].startsWith("Total snapshots:"))
     	    	{
     	    		numSnapshots = Integer.parseInt(lines[i].split(":")[i].trim());
     	    	}
     	    }
    	}
    	return numSnapshots;
	}
	
	/* 
	 * Read in the generic MBR. Modify the end CHS address and size 
	 * bytes and write out to modifiedMBR 
	 */
	private static void modifyGenericMBR(long sizeOfPartition, int numHeads, File genericMBR, File modifiedMBR)
	{
		File inputFile, outputFile;
		FileInputStream  genericMBRIn = null;
		FileOutputStream  modifiedMBROut = null;

		inputFile = genericMBR;
		outputFile = modifiedMBR;
		
		long sizeOfPartitionSectors = sizeOfPartition/512;
		final int SECTORS_PER_TRACK = 63;

		try
		{
			genericMBRIn = new FileInputStream(inputFile);
			modifiedMBROut = new FileOutputStream(outputFile);
			
			int c;
			for(int i = 0; i < genericMBR.length(); i++)	//0-511 bytes + 62 blank sectors
			{
				c = genericMBRIn.read();
								
				//modify bytes 451-453 of generic MBR with ending CHS value for partition
				if(i == 451)
					c = numHeads;
				else if(i == 452)
				{
					c = SECTORS_PER_TRACK;
					int tmp = (int)(sizeOfPartitionSectors / numHeads / SECTORS_PER_TRACK); //temp cylinder val
					
					//handle bits 9 and 10 of the 10 bit cylinder value and attach them to the end of the 6 bit sector val to make a full byte
					if((tmp & 0x100) > 0)	
						c += 64;			//set bit 7 of sector byte (corresponds to bit 9 of cylinder)
					if((tmp & 0x200) > 0) 
						c += 128; 			//set bit 8 of sector byte (corresponds to bit 10 of cylinder)
				}
				else if(i == 453)
					c = (int)((sizeOfPartitionSectors / numHeads / SECTORS_PER_TRACK) & 0xFF);
				else if(i == 458)	// modify bytes 458-461 of generic MBR with size of partition
					c = (int)sizeOfPartitionSectors & 0xFF;							//byte 12 of bootable partition entry
				else if(i == 459)
					c = (int)((sizeOfPartitionSectors & 0xFF00) >> 8);				//byte 13 of bootable partition entry
				else if(i == 460)
					c = (int)((sizeOfPartitionSectors & 0xFF0000) >> 16);			//byte 14 of bootable parititon entry
				else if(i == 461)
					c = (int)((sizeOfPartitionSectors & 0xFF000000) >> 24);			//byte 15 of bootable partition entry
	
				modifiedMBROut.write(c);	//write the byte to the modified mbr
			}

			genericMBRIn.close();
			modifiedMBROut.close();
		}
		catch (IOException ioe)
		{
			postError("There was an I/O problem writing an MBR for your partition " + ioe.getMessage());
		}
	}
	
	/*
	 * Extracts the disk serial number from the registry
	 * 
	 * This is necessary for building the mbr for partitions because the disk serial number
	 * is used by some operating systems to maintain the drive to letter mappings (ie the bootable
	 * is 'c:') Without the correct serial number in the mbr, the OS may not be able to find that mapping
	 * in the registry and may hang when trying to boot. 
	 */
	private static int[] getNTDriveSerialNum(String vmdkLoc, boolean useExistingSnapshot, OperatingSystem os, int ntPartitionIndex)
	{
		String mountDriveLetter = MOUNT_DRIVE_LETTER; //ExternalConfigStrings.getString("Configuration.DriveMountLetter");
		String bootDriveLetter;
		int[] serialNum = {0,0,0,0};
		boolean worked;
		
		//if there is a snapshot we need to mount the snapshot and load registry because it was not done during prepareForLaunch
		if(useExistingSnapshot)
		{
			//mount snapshot to access registry
	    	worked = mountSnapshot(mountDriveLetter, vmdkLoc, ntPartitionIndex);	
	 
	    	if(!worked)
	    	{
	    		postError("Snapshot Mount For Serial Number Failed");
	    		serialNum = null;
	    	}
	    	else
	    		postOutput("Mounted Snapshot For Disk Serial Number: " + endL);
	    	
	    	//load system hive
	    	
	    	String systemHiveLoc = null;
	    	String systemDir = os.getSystemRoot();
	    	if(systemDir != null)
	    		systemHiveLoc = systemDir + "\\system32\\config\\system";
	    	else
	    	{
	    		postError("Could not locate system hive for serial number extraction");
	    		serialNum = null;
	    	}

	    	if(serialNum != null && systemHiveLoc != null)	//we know it is either an XP or 2K aliased OS
	    		worked = loadSystemHive(systemHiveLoc);
	 
	    	if(!worked)
	    	{
	    		postError("System Hive Load For Serial Number Failed");
	    		serialNum = null;
	    	}
	    	else
	    		postOutput("Loaded System Hive For Disk Serial Number: " + endL);
		}
    	
    	//get registry key and parse out boot drive letter (mounted snapshot and registry should be open and loaded already from prepareForLaunch())
    	bootDriveLetter = getBootDriveLetter();	
    	
    	if(bootDriveLetter != null)
    	{
    		postOutput("Got bootable partition drive letter mapping: " + bootDriveLetter + endL);
    
    		
    		//use boot drive letter to get serial number out of HKLM\NEWSYSTEM\MountedDevices\DosDevices
    		String searchStr = "\\DosDevices\\" + bootDriveLetter.toUpperCase() + ":";
    		String type = "REG_BINARY";
            String regData = queryRegistry("HKLM\\NEWSYSTEM\\MountedDevices\\",
    										searchStr,
    										type);
           
            String diskSerialString = "-1";

            if(regData != null)
            {
            	diskSerialString = regData.substring(0,8); 	//chop off first 8 characters (4 hex bytes)
    			
            	//convert hex string to integer array
    			long val = Long.parseLong( diskSerialString, 16 );
    			serialNum[0] = (int) ( ( val >>> 24 ) & 0xff );
    			serialNum[1] = (int) ( ( val >>> 16 ) & 0xff );
    			serialNum[2] = (int) ( ( val >>> 8 )  & 0xff );
    			serialNum[3] = (int) ( ( val >>> 0 )  & 0xff );
    			
    			postOutput("Disk Serial Number Extracted Successfully " + endL);
            }
            else
            {
            	postError("Bootable Drive Letter Does Not Match Any Mounted Device Entries");
            	serialNum = null;
            }
    	}
    	else
    	{
    		postError("Failed to get bootable partition's drive letter mapping");
    		serialNum = null;
    	}

    	//unload system hive
    	worked = unloadHive("SYSTEM");

    	if(!worked)
    	{
    		postError("System Hive Unload For Serial Number Failed");
    		serialNum = null;
    	}
    	else
    		postOutput("System Hive Unloaded Successfully " + endL);
    	
    	//unmount snapshot
    	worked = unmountSnapshot(mountDriveLetter, false);	
    	
    	if(!worked)
    	{
    		postError("Snapshot Unmount For Serial Number Failed");
    		serialNum = null;
    	}
    	else
    		postOutput("Snapshot Unmounted Successfully " + endL);
    
		return serialNum;
	}
	
	/* 
	 * Precondition: Registry is already loaded and will be unloaded after method returns 
	 * return null means failure querying registry
	 */
	private static String getBootDriveLetter()
	{
    	//search registry key HKLM\NEWSYSTEM\ControlSet001\Control\ContentIndex\DllsToRegister to find bootable drive letter
        String regData = queryRegistry("HKLM\\NEWSYSTEM\\ControlSet001\\Control\\ContentIndex",
										"DllsToRegister",
										"REG_MULTI_SZ");

        if(regData != null)
        	return  regData.substring(0,1); 	//chop off the first character (drive letter);
        
        return null;			//unknown string found
	}
	
	/*
	 * Unmounts mounted image and unloads the registry (for instance when program is closed prematurely)
	 */	
	private static void cleanUp()
	{
		if(startWasClicked && externalProc != null)
		{
			postOutput("Cleaning up..." + endL);
			String mountDriveLetter = MOUNT_DRIVE_LETTER; //ExternalConfigStrings.getString("Configuration.DriveMountLetter");
			unmountSnapshot(mountDriveLetter, true);	//force unmount of disk image
			unloadHive("SYSTEM");
			unloadHive("SOFTWARE");
		}
	}
	
	/* 
	 * Finds next open drive letter on system starting at the startVal
	 */
	private static String getNextFreeDriveLetter(char startVal)
	{
    	File testMountDriveLetter; 
    	char curLetter = startVal;
    	
    	while(curLetter <= 'z')
    	{
    		testMountDriveLetter = new File(curLetter + ":");
        	if(!testMountDriveLetter.isDirectory())
        		return Character.toString(curLetter);	//found open drive
        	curLetter++;	//try next character
    	}
    	return null;	//error, all drive letter used
	}
	
	/*
	 * Sorts the file extensions of chunked images so that they can be 
	 * put in order before attempting to boot the image
	 * 
	 * Sorts purely numeric extensions numerically, and purely alphabetic and alphanumeric
	 * extensions alphabetically
	 */
	private static void sortChunkFileNamesByExtension(String[] chunkFiles)
	{
		String[] extensions = new String[chunkFiles.length];		//string file extensions for chunkFiles
		int[]	 numericExtensions = new int[chunkFiles.length];	//numeric file extensions
		boolean isNumber = false;
		boolean inconsistent = false; //keeps track of whether all extensions are numerical or if they are mixed
		
		for(int i = 0; i < chunkFiles.length; i++)
		{
			String curFile = chunkFiles[i];
			int offset = curFile.lastIndexOf('.');
			
			if (i >= 0 && i < chunkFiles.length)
			{
				extensions[i] = curFile.substring(offset + 1).toLowerCase();
				
				int extValDec = -1;

				try
				{
					extValDec = Integer.parseInt(extensions[i]);	//check if extension is an integer
					numericExtensions[i] = extValDec;
					isNumber = true; 
				}
				catch(NumberFormatException nfe)
				{
					if(isNumber == true)
						inconsistent = true;
				}			
			}
		}

		if(isNumber && !inconsistent)	//if we have all numeric extensions
		{
			//basic bubble sort of file extensions and file names at the same time
			for(int i = 0; i < extensions.length - 1; i++)
			{
				for(int j = 0; j < extensions.length - i - 1; j++)
				{
					int tempInt;
					String tempStr;
					if(numericExtensions[j+1] < numericExtensions[j])
					{
						tempInt = numericExtensions[j];
						tempStr = chunkFiles[j];
						
						numericExtensions[j] = numericExtensions[j+1];
						chunkFiles[j] = chunkFiles[j+1];
						
						numericExtensions[j+1] = tempInt;
						chunkFiles[j+1] = tempStr;
					}
				}
			}
//			System.out.println("Sorted Numerically: " + Arrays.toString(chunkFiles));
		}
		else	//not numeric extensions (or mixed extensions)
		{
			Arrays.sort(chunkFiles);	//sort alphabetically
//			System.out.println("sorted alphabetically: " + Arrays.toString(chunkFiles));
		}
	}
	
	/*
	 * Checks if the current machine has vmware server or vmware workstation installed
	 *  1  	= vmware server
	 *  0 	= vmware workstation
	 * -1 	= no vmware installation detected
	 *
	 */
	private static int isUsingVMWareServer()
	{
		String vmType = queryRegistryForVMWareCore();//ExternalConfigStrings.getString("Configuration.VMWareVersion");
		
		if(vmType == null)
			return -1;
		
		if(vmType.compareToIgnoreCase("VMware Server Standalone") == 0)
			return 1;
		else
			return 0;	
	}
	
	/*
	 * Check HKLM\SOFTWARE\VMWare, Inc.\Core for:
	 *  VMware Server Standalone
	 *   or
	 *  VMware Workstation 
	 *  
	 *  Workstation and Server cannot be installed simultaneously (although
	 *  registry entries from both may be present) so this key determines
	 *  whether Worstation or Server is the "active" installation
	 *  
	 */
	private static String queryRegistryForVMWareCore()
	{
    	//search registry key HKLM\SOFTWARE\VMWare, Inc.\Core
    	String[] cmd = new String[5];
    	cmd[0] = "reg";
    	cmd[1] = "query";	
    	cmd[2] = "\"" + "HKLM\\SOFTWARE\\VMWare, Inc." + "\"";
    	cmd[3] = "/v";
    	cmd[4] = "Core";

        String regData = queryRegistry("HKLM\\SOFTWARE\\VMWare, Inc.",
        								"Core",
        								"REG_SZ");
        
	    if(regData == null)
	    	return regData;
	    else if(regData.equalsIgnoreCase("VMware Server Standalone") || regData.equalsIgnoreCase("VMware Workstation"))
	    	return regData;		//return the core string
	    
	    return null;			//unknown string found
	    	
	}
	
	/*
	 * Query WMI Interface for physical devices attached to machine
	 * 
	 * Returns array of combo box items representing the display string and underlying physical device address value pairs
	 * null indicates the query failed could not be found
	 * 
	 */
	private static PhysicalDiskInfo[] getPhysicalDeviceItems()
	{
		ArrayList diskInfoList = new ArrayList();
		PhysicalDiskInfo[] returnVal = null;
		
		//wmic /namespace:\\root\cimv2 path Win32_DiskDrive get index, InterfaceType, model, size
    	String[] cmd = new String[9];
    	cmd[0] = "wmic";
    	cmd[1] = "/namespace" + ":\\\\root\\cimv2";	
    	cmd[2] = "path";
    	cmd[3] = "Win32_DiskDrive";
    	cmd[4] = "get";
    	cmd[5] = "index";
    	cmd[6] = ",InterfaceType";
		cmd[7] = ",model";
		cmd[8] = ",size";
    	
		
		String outputBuffer = callExternalProcess(cmd);
		
		if(outputBuffer == null)
			return null;
		
		String[] outputLines = outputBuffer.split(System.getProperty("line.separator"));
    		
		String line;
        
        String index, interfaceType, model;
        double size;

        for(int i = 0; i < outputLines.length; i++)	//for every output line
        {
        	line = outputLines[i];
        	if(!line.trim().startsWith("Index") && !line.equals(endL))	//if line is not a column header line
        	{	
        		String[] colVals = line.trim().split("\\s\\s+", 4);	//4 chunks separaed by 2 or more spaces
        		
        		if(colVals.length != 4)
        			return null;
        		
        		index = colVals[0];
        		interfaceType = colVals[1];
        		model = colVals[2];
        		size = Double.parseDouble(colVals[3]);
        		
        		PhysicalDiskInfo pdi = new PhysicalDiskInfo(index, interfaceType, model, size);
        		diskInfoList.add(pdi);
        	}
        	else
        		continue; //skip the column headers
        }
		    		
		//convert arraylist into array of comboboxitems
		returnVal = new PhysicalDiskInfo[diskInfoList.size()];
	    for(int i = 0; i < diskInfoList.size(); i++)
	    {
	    	returnVal[i] = (PhysicalDiskInfo)diskInfoList.get(i);
	    }
    	   
	    return returnVal;
	}
	
	/*
	 * Searches the registry for a particular key and returns its associated value
	 * 
	 * example: regPath: 	"HKLM\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion"
	 * 			regKeyName: "ProductName"
	 * 			regType:	"REG_SZ"
	 * 
	 * null indicates the key could not be found
	 */
	public static String queryRegistry(String regPath, String regKeyName, String regType)
	{
    	//search registry for regPath\regKeyName
    	String[] cmd = new String[5];
    	cmd[0] = "reg";
    	cmd[1] = "query";	
    	cmd[2] = "\"" + regPath + "\"";
    	cmd[3] = "/v";
    	cmd[4] = regKeyName;

        String regData = null;
        
		String outputBuffer = callExternalProcess(cmd);
		
		if(outputBuffer == null)
			return null;
		
		String[] outputLines = outputBuffer.split(System.getProperty("line.separator"));
    		
		String line;
        boolean done = false;
        int offset;
        
        for(int i = 0; !done && i < outputLines.length; i++)	//for every output line
        {
        	line = outputLines[i];
        	if(line.trim().startsWith(regKeyName))
        	{
        		offset = line.indexOf(regType) + regType.length();
        		regData = line.substring(offset,line.length()).trim();
        		done = true;
        	}
        }
        return regData;
	}
	
	/*
	 * Build the path to the vmrun executable (which is different based
	 * on whether using VMWare server or Workstation. 
	 */
	private static String getVMWareVMRunPath(boolean isVMWareServer)
	{
		String installDir = getVMWareInstallDir(isVMWareServer);
		if(isVMWareServer)
			return installDir + "..\\VMware VIX\\vmrun.exe";
		else
			return installDir + "\\vmrun.exe";
	}

	
	/*
	 * Check HKLM\SOFTWARE\VMWare, Inc.\VMware {Server|Workstation}\InstallPath for:
	 *  VMware Server Standalone
	 *   or
	 *  VMware Workstation 
	 */
	private static String getVMWareInstallDir(boolean isVMWareServer)
	{
		StringBuffer regPath = new StringBuffer("HKLM\\SOFTWARE\\VMware, Inc.\\");
		if(isVMWareServer)
			regPath.append("VMware Server");
		else
			regPath.append("VMware Workstation");
		
		return queryRegistry(regPath.toString(), "InstallPath", "REG_SZ");

	}
	
	/*
	 * Check HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\vmware-mount.exe\Path 
	 * for the path to the vmware-mount.exe executable
	 */
	private static String queryRegistryForVMMountPath()
	{

		String path = queryRegistry("HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\vmware-mount.exe", "Path", "REG_SZ");
		if(path != null)
			return path + "\\vmware-mount.exe";
		return path;
	}
	
	
	/*
	 * Kills the external process represented by global proc
	 */
	private static void stopProc()
	{
		if(worker != null)
			worker.interrupt();	//interrupt running process
		if(externalProc != null)
			externalProc.destroy();		//kill the running process
	}
	
	/*
	 * Posts an error message to the GUI output area
	 */
	public static void postError(String line)	//write an error message
	{
		messageOutputArea.append("ERROR>     " + line + endL);	//write the error message to out window
		messageOutputArea.setCaretPosition(messageOutputArea.getText().length());	//scroll down
	}

	/*
	 * Utility method used for posting output messages to the GUI output area
	 */
	public static void postOutput(String line)  //write a line of output to out window
	{
		final String fLine = line;
		Runnable doAppendJTextArea = new Runnable() 	//to prevent gui lockup
		{
            public void run() 
            {
                messageOutputArea.append(fLine);	//output console line
                // Scroll down as text is output
                messageOutputArea.setCaretPosition(messageOutputArea.getText().length());
            }
        };
        SwingUtilities.invokeLater(doAppendJTextArea);
	}
}

