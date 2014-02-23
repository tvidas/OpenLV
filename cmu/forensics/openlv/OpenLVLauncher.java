/*
   OpenLVLauncher

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

import cmu.forensics.mbr.MasterBootRecord;
import cmu.forensics.registry.RegistryParser;   /*#####Gov#####*/
import java.awt.*;
import java.awt.event.*;

import java.text.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Properties;
import java.util.*;
import java.util.zip.*;


/**
 * OpenLVLauncher
 * OpenLV is a tool that takes a bit-by-bit image of a disk (such as those created with dd) 
 * and allows a user to boot it up in VMWare without modifying the image, All writes to disk 
 * are directed to a VMWare redo file which can be deleted (reverting all changes) 
 * 
 * OpenLV handles:
 *  - DD style full disk images
 *  - DD style images of partitions (builds a custom mbr for the partition)
 *  - Hard disks (with the aid of a usb or firewire writeblocking bridge)
 *  - Mounted images with the aid of mounting software such as Mount Image Pro
 *  - Split dd style images
 *  - VMware Workstation or Free VMware Server
 *                        
 * It allows you to:      
 *  - Specify the system time (set to time of seizure)
 *  - Dump the sam/system/security hives for password cracking (windows only) #####Gov#####
 *  - Dump the sam and mscash password hashes for password cracking (windows only) #####Gov#####
 *  - Blank out all user account passwords on the system (windows only) #####Gov#####
 *  - Continue working with your changes or start form scratch
 *  - Generate only the configuration files (user launches vmx manually) or automate the launch
 *                        
 * It also makes the necessary changes (installing a vmware compatible intel driver) to XP/2K 
 * systems to correct the 0x7B bluescreen issue when booting up a system installed on non-intel 
 * hardware               
 *                        
 * The Main OpenLV Class that launches the GUI, validates input, builds the config files, makes 
 * modifications to VM redo, launches the VM, etc 
 * @author Tim Vidas
 * @author Brian Kaplan
 * @version 0.8Beta, Dec 2009
 */

public class OpenLVLauncher 
{   
    private static String      	     jarFile = "OpenLV.jar";
    private static final String      PermLogFile = "OpenLV.log";
    private static final String      TempLogFile = "MostRecentRun.log";
    public  static final String      endL = System.getProperty("line.separator");
    public  static final String      slash = System.getProperty("file.separator");
    private static LogWriter         myLogWriter = new LogWriter(TempLogFile,PermLogFile);

    private static final JTextArea   messageOutputArea = new JTextArea();   //program output area
    private static SwingWorker       worker;   
    private static Process           externalProc;   //external process(es) that are called throughout
    private static final Date        now = new Date();   //current time/date

    //private static final String    dateFormat = InternalConfigStrings.getString("OpenLVLauncher.SystemTimeFormat");
    private static final String      PARENT_DISK_SIZE_GB = "950";   //parent disk size for partitions (arbitrary large size -- was previously a user supplied value)
    private static       String      guestOSTypeText;   

    private static boolean           startWasClicked     = false;   //keeps track of whether launch button was clicked or not

    /* Windows host specific */
    //private static final String      MOUNT_DRIVE_LETTER  = getNextFreeDriveLetter('k');   //get next free drive letter for mounting   
    private static String  	     MOUNT_DRIVE_LETTER  ;
    private static String      	     VMWARE_REG_PATH     ;
    //private static final String      VMWARE_REG_PATH     = getVMWareRegPath();      //get reg path for VMWare reg or WOW64
    private static int               vmWareInstallType   ;  //pushed to later on, so there aren't any errors during instantiation

    private static boolean           isVMWareServer      ;  //pushed to later on, so there aren't any errors during instantiation
    private static String            VMWARE_VMRUN_PATH   ; //pushed to later du to isVMWareServer move = getVMWareVMRunPath(isVMWareServer);      //path to vmrun executable
    //private static final String      VMWARE_MOUNT_PATH   = queryRegistryForVDDKVMMountPath();
    private static String      	     VMWARE_MOUNT_PATH   ;

    /* Linux host specific */
    private static String	     VIRTUAL_BOX_PATH    ;


    private static final double      JVM_MINIMUM_REQ     = 1.6;         //requires jvm 1.6 or higher
    public static final long         BYTES_PER_GIG       = 1073741824;   //2^30 bytes per gig
    public static final long         BYTES_PER_MB        = 1048576;

    private static String            thisComputerVMWare   = null;
    private static String            thisComputerRAM      = null;
    private static String            thisComputerOS       = null;
    private static String            thisComputerOSType   = null;
    private static String            thisComputerSP       = null;
    private static String            thisComputerArch     = null;
    private static String            thisComputerJava     = null;

    //these should really be in a seperate 'include' file that this class 'implements'
    public static final int          verWorkstationOld = 3;
    public static final int          verWorkstation = 0;
    public static final int          verServerOne   = 1;
    public static final int          verServerTwo   = 2;
    public static final int          verError       = -1;
    public static String             dataDisks      = "";

    public static final String       FORMAT = "MMM dd yyyy HH:mm:ss z";
    public static final SimpleDateFormat formatter = new SimpleDateFormat(FORMAT);

    private static String            confFile = System.getProperty("user.home") + slash + "OpenLV.ini";   //use user's home directory
    private static ExternalConfigStrings ecs = new ExternalConfigStrings(confFile);
    private static final boolean     enableAdvanced = (getConfString("EnableAdvancedFeatures").equalsIgnoreCase("true"))? true : false;
    private static String	     virtualizationSoftware = getConfString("VirtualizationSoftware");
    private static final String      MAX_PHYS_DRIVE_NUM_STR = getConfString("MaxPhysicalDrives");
    private static ArrayList<DiskData> ddList = new ArrayList<DiskData>(); 

    public static String	     FILE_SEP_STRING = ",   ";  //used to seperate file names, particularly in multi file input (eg split/chunked images)

    private static String[] guestOSVals;
    private static String[] osVals;


    /**
     * the main funtion is what spins OpenLV up
     */
    public static void main(String args[])
    {       
        jarFile = "OpenLVGovernment.jar";  // #####Gov#####
        myLogWriter.log(InternalConfigStrings.getString("OpenLVLauncher.TitleBarText"));
	thisComputerOS = System.getProperty("os.name");
	thisComputerSP = System.getProperty("os.version");
	thisComputerArch = System.getProperty("os.arch");
        myLogWriter.log("Host Operating System: " + thisComputerOS + " " + thisComputerSP);


        FILE_SEP_STRING = getConfString("FileSeperator"); //allow user to specify the file seperator used in the GUI disk image selection box

        //JFrame.setDefaultLookAndFeelDecorated(true);   //Java style GUI

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
        final JFrame frame = new JFrame(InternalConfigStrings.getString("OpenLVLauncher.TitleBarText")); 
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);     

        //Set the frame's icon to an image loaded from a file.
        frame.setIconImage(new ImageIcon(InternalConfigStrings.getString("OpenLVLauncher.FrameIconPath")).getImage());

        //maximize to user specified percentage of screen
        int percentWidth  = Integer.parseInt(InternalConfigStrings.getString("OpenLVLauncher.PercentageOfScreenWidth"));
        int percentHeight = Integer.parseInt(InternalConfigStrings.getString("OpenLVLauncher.PercentageOfScreenHeight"));
        percentHeight+=2;   /*#####Gov#####*/   //this is added to correct the difference in GUI size resulting from removing password/hive functionality

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

        frame.setBounds(0,0,(int)(screenSize.width * percentWidth/100) ,(int)(screenSize.height * percentHeight/100));


            /* Linux support is in beta  ymmv */
            boolean userIsOkWithLinux = false;
            if (System.getProperty("os.name").toUpperCase(java.util.Locale.ENGLISH).indexOf("LINUX") != -1)
            {
		thisComputerOSType = "LINUX";
                Object[] options = {"Okay"};   //button titles
                int answer = JOptionPane.showOptionDialog(frame, 
                        "Linux support is in beta, YMMV. Select OK to continue anyway.",
                        "Detected A Non-Windows Operating System",
                        JOptionPane.OK_OPTION,
                        JOptionPane.ERROR_MESSAGE,
                        null,              //no custom icon
                        options,           //the titles of buttons
                        options[0]);       //default button title

                if(answer == JOptionPane.OK_OPTION)   //user clicked ok button
                {
			userIsOkWithLinux = true;
                }
            }
            if (System.getProperty("os.name").toUpperCase(java.util.Locale.ENGLISH).indexOf("WINDOWS") != -1)
	    {
		thisComputerOSType = "WINDOWS";
 	    }

            /* Check that the system is running Windows */
            /* JAY - the Locale.ENGLISH is a 'fix' for turkish - http://java.sys-con.com/node/46241 */
            if ((System.getProperty("os.name").toUpperCase(java.util.Locale.ENGLISH).indexOf("WINDOWS") == -1) && !userIsOkWithLinux)
            {
                Object[] options = {"Okay"};   //button titles
                int answer = JOptionPane.showOptionDialog(frame, 
                        "Sorry, OpenLV is Currently a Windows Only Application" + endL 
                        + "You appear to be running: " + System.getProperty("os.name"),
                        "Detected A Non-Windows Operating System",
                        JOptionPane.OK_OPTION,
                        JOptionPane.ERROR_MESSAGE,
                        null,              //no custom icon
                        options,           //the titles of buttons
                        options[0]);       //default button title

                if(answer == JOptionPane.OK_OPTION)   //user clicked ok button
                {
                    System.exit(1);
                }
	    }
 
            if(isWindows()){
			postOutput("OSTYPE : " + thisComputerOSType);
    			VMWARE_REG_PATH     = getVMWareRegPath();      //get reg path for VMWare reg or WOW64
    			MOUNT_DRIVE_LETTER  = getNextFreeDriveLetter('k');   //get next free drive letter for mounting   
    			VMWARE_MOUNT_PATH   = queryRegistryForVDDKVMMountPath();
	    }
	    if(isLinux()){
			MOUNT_DRIVE_LETTER  = getMountPoint("/mnt");  //TODO FIXME make a random mount point
    			VMWARE_MOUNT_PATH   = findBinary("vmware-mount");   //find the mount binary
	    }



            DisplayBetaWarning(frame);
        if(virtualizationSoftware != null){
		myLogWriter.log("virtualization set to " + virtualizationSoftware);
		if(!(virtualizationSoftware.equals("VMWare") || 
		   virtualizationSoftware.equals("VirtualBox") || 
		   virtualizationSoftware.equals("Automatic"))){

			Object[] options = {"Okay"};   //button text
			int answer = JOptionPane.showOptionDialog(frame,
				"OpenLV only works with VMWare and VirtualBox, the configured setting of " + virtualizationSoftware + " won't work.",
				"Detected bad VirtualizationSoftware settings: " + virtualizationSoftware,
				JOptionPane.OK_OPTION,
				JOptionPane.ERROR_MESSAGE,
				null,              //no custom icon
				options,           //the titles of buttons
				options[0]);       //default button title

			if(answer == JOptionPane.OK_OPTION)   //user clicked ok button
			{
			    System.exit(1);      //close program
			}

			
		}
        }

            /* figure out virtualization platform */
            if( virtualizationSoftware.equals("Automatic")){
		if(getVMWareVersion() >= 0){
			virtualizationSoftware = "VMWare";
		}
		else if(getVirtualBoxVersion() >= 0){
			virtualizationSoftware = "VirtualBox";
		}
		else {
                        Object[] options = {"Okay"};   //button text
                        int answer = JOptionPane.showOptionDialog(frame,
                                "OpenLV only works with VMWare and VirtualBox and can't automatically determine that either is installed.  Consider configuring this setting manually or reporting a bug .",
                                "Can't detect virtualization software",
                                JOptionPane.OK_OPTION,
                                JOptionPane.ERROR_MESSAGE,
                                null,              //no custom icon
                                options,           //the titles of buttons
                                options[0]);       //default button title

                        if(answer == JOptionPane.OK_OPTION)   //user clicked ok button
                        {
                            System.exit(1);      //close program
                        }
			

		}
	    }

        /* Program Output Area */
        int outPtSize = Integer.parseInt(InternalConfigStrings.getString("OpenLVLauncher.OutputPointSize"));
        messageOutputArea.setFont(new Font("Veranda", Font.PLAIN, outPtSize)); //fixed width font will look best
        messageOutputArea.setLineWrap(true);
        messageOutputArea.setWrapStyleWord(true);

        int outCols = Integer.parseInt(InternalConfigStrings.getString("OpenLVLauncher.OutputWindowWidth"));
        int outRows = Integer.parseInt(InternalConfigStrings.getString("OpenLVLauncher.OutputWindowHeight"));
        messageOutputArea.setColumns(outCols);
        messageOutputArea.setRows(outRows);
        messageOutputArea.setMargin(new Insets(5,5,0,0));
        messageOutputArea.setEditable(false);

        JPanel outPanel = new JPanel(new BorderLayout());
        outPanel.add(new JScrollPane(messageOutputArea), BorderLayout.CENTER);
        outPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createBevelBorder(2),"Messages")); 

        /* RAM Field */
        final JTextField sizeRamField = new JTextField();
        sizeRamField.setColumns(7);   //set text field width
        sizeRamField.setText(getConfString("DefaultRamSize"));
        sizeRamField.setToolTipText(InternalConfigStrings.getString("OpenLVLauncher.ToolTipRamSize"));

        /* RAM Size Label */
        final JLabel sizeRamLabel = new JLabel(InternalConfigStrings.getString("OpenLVLauncher.RamSizeLabel")); 

        JPanel sizeRamPanel = new JPanel(new GridLayout(3,0));
        sizeRamPanel.add(sizeRamLabel);   //add label
        sizeRamPanel.add(sizeRamField,BorderLayout.SOUTH);      //add text field

        /* System Time Field */
        final JTextField systemTimeField = new JTextField();
        systemTimeField.setColumns(20);   //set text field width
       // DateFormat df = DateFormat.getDateTimeInstance();
        String formattedDate = null;
        //String FORMAT = "MMM dd yyyy HH:mm:ss z";
        //SimpleDateFormat formatter = new SimpleDateFormat(FORMAT);
               if(getConfString("DefaultSystemTime").compareTo("<NOW>") == 0)   //if user specifies <NOW> use current date/time as default sys time
               {
                 formattedDate = formatter.format(now);
                 //formattedDate = df.format(now);
        
               }
               else
		{
			formattedDate = getConfString("DefaultSystemTime");
		       postOutput("Using Custom Configured Time value");
		  }
	    
	  systemTimeField.setText(InternalConfigStrings.getString("OpenLVLauncher.DefaultSystemTime"));
	  systemTimeField.setText(formattedDate);

        //DateFormat df = DateFormat.getDateTimeInstance();
        //String formattedDate = null;
        //formattedDate = df.format(now);
        //systemTimeField.setText(formattedDate);

        systemTimeField.setToolTipText(InternalConfigStrings.getString("OpenLVLauncher.ToolTipSystemTime"));

        /* System Time Label */
        final JLabel systemTimeLabel = new JLabel(InternalConfigStrings.getString("OpenLVLauncher.SystemTimeLabel")); 

        /* System Time Group (Panel) */
        JPanel systemTimePanel = new JPanel(new GridLayout(3,0));
        systemTimePanel.add(systemTimeLabel);   //add label
        systemTimePanel.add(systemTimeField,BorderLayout.SOUTH);      //add text field

        /*##########start-gov##########*/
        /* Checkbox for Clear Local Login Passwords */
        final JCheckBox clearPWBox = new JCheckBox(InternalConfigStrings.getString("OpenLVLauncher.ClearPasswordLabel"));    
        if(getConfString("DefaultClearPassword").equalsIgnoreCase("true"))             
        {                                                                                        
            //clearPWBox.setEnabled(true);                                                                
            clearPWBox.setSelected(true);                                                                
        }                                                                                        
        else                                                                                     
        {                                                                                        
            //clearPWBox.setEnabled(false);   //grayed out                                                    
            clearPWBox.setSelected(false);                                                                
        }                                                                                        
        clearPWBox.setToolTipText(InternalConfigStrings.getString("OpenLVLauncher.ToolTipClearPassword"));                   

        /* Checkbox for Clear Cached Domain Creds */
        final JCheckBox clearDomainPWBox = new JCheckBox(InternalConfigStrings.getString("OpenLVLauncher.ClearDomainPasswordLabel"));    
        if(getConfString("DefaultClearDomainPassword").equalsIgnoreCase("true") && enableAdvanced)             
        {                                                                                        
            //clearDomainPWBox.setEnabled(true);                                                                
            clearDomainPWBox.setSelected(true);                                                                
        }                                                                                        
        else                                                                                     
        {                                                                                        
            //clearDomainPWBox.setEnabled(false);   //grayed out                                                    
            clearDomainPWBox.setSelected(false);                                                                
        }                                                                                        
        clearDomainPWBox.setToolTipText(InternalConfigStrings.getString("OpenLVLauncher.ToolTipClearDomainPassword"));                   

        /* Checkbox Dumping SAM File */       
        final JCheckBox dumpSAMBox = new JCheckBox(InternalConfigStrings.getString("OpenLVLauncher.DumpHivesLabel"));    
        if(getConfString("DefaultDumpHives").equalsIgnoreCase("true"))   //based on default value    
        {                            
            //dumpSAMBox.setEnabled(true);    
            dumpSAMBox.setSelected(true);    
        }                            
        else                         
        {                            
            //dumpSAMBox.setEnabled(false);   //grayed out    
            dumpSAMBox.setSelected(false);                
        }                            

        dumpSAMBox.setToolTipText(InternalConfigStrings.getString("OpenLVLauncher.ToolTipDumpHives"));    

        /* Put the two check boxes (clear pw and dump sam) in their own panel */    
        JPanel samPanel = new JPanel(new GridLayout(3,0));    
        samPanel.add(clearPWBox);   //add label       
        samPanel.add(dumpSAMBox);//,BorderLayout.NORTH);      //add text field    
	if(enableAdvanced){
	samPanel.add(clearDomainPWBox);//,BorderLayout.SOUTH);
	}
        /*##########end-gov##########*/   

        /* OS Selection Combo Box */
        //map the user displayed OS choice vals to the vmware guest os values 
        if(isVMWare()){
		guestOSVals = InternalConfigStrings.getString("OpenLVLauncher.GuestOSVals").split(","); //get the vmware guest OS values from properties file
		osVals = InternalConfigStrings.getString("OpenLVLauncher.OSChoices").split(","); //get the corresponding plain text os choices from properties file
        }
        if(isVirtualBox()){
		getVirtualBoxOSTypes();
	}

        final JComboBox osSelectionCombo = new JComboBox();   
        ComboBoxItem cbItem = null;

        //fill combo box with the OS values/guest os val pairs from above
        for(int i = 0; i < guestOSVals.length && i < osVals.length; i++)
        {
            cbItem = new ComboBoxItem(osVals[i], guestOSVals[i]);
            osSelectionCombo.addItem(cbItem);
        }
        osSelectionCombo.setSelectedIndex(0);   //default value is the first value
	//osSelectionCombo.setPreferredSize(new Dimension(outPtSize+5,outPtSize+5));
        osSelectionCombo.setToolTipText(InternalConfigStrings.getString("OpenLVLauncher.ToolTipOSSelection"));

        /* OS Selection Label */
        final JLabel osSelectionLabel = new JLabel(InternalConfigStrings.getString("OpenLVLauncher.OSSelectionLabel"));
	//osSelectionLabel.setPreferredSize(new Dimension(outPtSize+5,outPtSize+5));
        final JLabel osSelectionDummy = new JLabel(" ");

        /* OS Selection Group (Panel) */
        JPanel osSelectionPanel = new JPanel(new GridLayout(3,0));
        osSelectionPanel.add(osSelectionLabel);      //add label
        osSelectionPanel.add(osSelectionCombo);      //add combo box
        osSelectionPanel.add(osSelectionDummy);      //add combo box

        /*##########start-gov##########*/
        //listener that enables/disables the clear password and dump sam checkbox based on OS    
        osSelectionCombo.addActionListener(new ActionListener()           
                {                                                 
		    public void actionPerformed(ActionEvent e)                 
			{                                              
			    JComboBox cb = (JComboBox)e.getSource();             
			    String osString = (String)((ComboBoxItem)cb.getSelectedItem()).getUnderlyingValue();   

			    if(!osString.equals("auto") &&
				OperatingSystem.getBaseOS(osString).compareTo("unknown") == 0 ||     
				OperatingSystem.getBaseOS(osString).compareTo("nt") == 0 ||          
				OperatingSystem.getBaseOS(osString).compareTo("98") == 0 ||           
				OperatingSystem.getBaseOS(osString).compareTo("me") == 0)   //any os where clear passwords/dump sam is not supported    
			    {
				clearPWBox.setEnabled(false);   //grayed out       
				clearPWBox.setSelected(false);                   
				dumpSAMBox.setEnabled(false);                   
				dumpSAMBox.setSelected(false);                   
			    } else
			    {                                           
			        clearPWBox.setEnabled(true);                   
			        clearPWBox.setSelected(true);                   
			        dumpSAMBox.setEnabled(true);                   
			        dumpSAMBox.setSelected(true);                   
			    }                                           
			    if(osString.equals("auto") || 
				OperatingSystem.getBaseOS(osString).compareTo("nt") == 0 ||     
				OperatingSystem.getBaseOS(osString).compareTo("2k") == 0 ||     
				OperatingSystem.getBaseOS(osString).compareTo("xp") == 0 ||     
				OperatingSystem.getBaseOS(osString).compareTo("2003") == 0 )
			    {
				clearDomainPWBox.setEnabled(true);  
				clearDomainPWBox.setSelected(true);                   
			    }else{
				clearDomainPWBox.setEnabled(false);  
				clearDomainPWBox.setSelected(false);                   
			    }
			}                                              
		}
	);                                                 
        /*##########end-gov##########*/       

        /* Radio Buttons for Generate Configs Only vs Generate and Launch VM */
        final JRadioButton generateAndLaunch    = new JRadioButton("Launch My Image",true); //default choice
        final JRadioButton generateOnly        = new JRadioButton("Generate Config Only");         

        generateOnly.setActionCommand("GenerateOnly");      //set action commands so we can tell what button is selected later 
        generateAndLaunch.setActionCommand("GenerateAndLaunch"); 

        generateOnly.setToolTipText(InternalConfigStrings.getString("OpenLVLauncher.ToolTipGenerateOnly"));   //share tool tip for option group
        generateAndLaunch.setToolTipText(InternalConfigStrings.getString("OpenLVLauncher.ToolTipGenerateAndLaunch"));

        //Put the mode option buttons into a mutually exclusive button group
        final ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(generateAndLaunch);
        modeGroup.add(generateOnly);

        /*  mode (generate config files only or generate and launch vm) group (Panel) */
        JPanel modePanel = new JPanel();
        modePanel.add(generateAndLaunch);   //add the two buttons to the panel
        modePanel.add(generateOnly);   
        modePanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createBevelBorder(2),"What do you want to do?"));   

        //input file and output dir selection
        final JFileChooser outDirChooser = new JFileChooser();
        final JFileChooser inFileChooser = new JFileChooser();

        //Set up browse for output dir section
        final JTextField directoryField = new JTextField();
        directoryField.setColumns(30);

        if(getConfString("DefaultOutputDirectory").compareTo("<HOME>") == 0)   //if properties file specifies "<HOME>"
            directoryField.setText(System.getProperty("user.home"));   //use user's home directory
        else
            directoryField.setText(getConfString("DefaultOutputDirectory"));   //use dir specified in properties file

        directoryField.setToolTipText(InternalConfigStrings.getString("OpenLVLauncher.ToolTipOutputDirectory"));

        final JButton browseOutputButton = new JButton("Browse"); 

        /* Browse button click action */
        browseOutputButton.addActionListener(new ActionListener()
                {
                    public void actionPerformed(ActionEvent e)
        {
            outDirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);   //only allow directories
            //             outDirChooser.setCurrentDirectory(new File("c:\\Temp"));    //TMV
            int returnVal = outDirChooser.showDialog(frame,"Select Directory"); 
            if (returnVal == JFileChooser.APPROVE_OPTION) 
        {
            File dir = outDirChooser.getSelectedFile();
            try
        {
            directoryField.setText(dir.getCanonicalPath());   //write the path in the text field
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
            int returnVal = inFileChooser.showDialog(frame,"Select File(s)"); 
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
                postError("The input file you selected is not recognized as valid, please select another.");
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
                        fNameBuf.append(FILE_SEP_STRING); //made adjustable because some users had ", " in the path - eg by name:  c:\people\Vidas, Tim\diskImage.001
                    inputFileField.setText(inFiles[i].getCanonicalPath());   //write the path in the text field
                }
                else
                    postError("The input file you selected is not recognized as valid, please select another.");
            }
            inputFileField.setText(fNameBuf.toString());   //write the paths to the selected files in the text field
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
                //             {
                //                //enable device selection combo
                //                physicalDeviceCombo.setVisible(true);
                //                physicalDeviceSelectionLabel.setVisible(true);
                //                //disable imagefile input box
                //                inputFileField.setVisible(false);
                //                browseInputButton.setVisible(false);

                if(e.getActionCommand().compareTo("PhysicalDisk") == 0)
                {
                    // fill physical device combo with info on physical devices     
                    PhysicalDiskInfo[] physicalDeviceItems = getPhysicalDeviceItems();

                    String displayString;
                    PhysicalDiskInfo currPDI = null;
                    physicalDeviceCombo.removeAllItems();
                    for(int i = 0; i < physicalDeviceItems.length; i++)
                    {
                        currPDI = physicalDeviceItems[i];
                        displayString = currPDI.getModel() + " (" + Math.ceil(currPDI.getSize()/BYTES_PER_GIG) + " GB)";

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

            //       //set the default source mode (choose image files, not physical device)
            //      inputFileField.setVisible(true);   //enable imagefile input box
            //      browseInputButton.setVisible(true);
            //      physicalDeviceCombo.setVisible(false);   //disable device selection combo
            //      physicalDeviceSelectionLabel.setVisible(false);
            //       
            /* Start Button */
            final  JButton    start       = new JButton("Start");
            start.setToolTipText(InternalConfigStrings.getString("OpenLVLauncher.ToolTipStartButton"));
            final  JMenuItem startItem   = new JMenuItem("Start");   //the menu start button

            /* Clear Button */
            final JButton    clear       = new JButton("Clear");
            clear.setToolTipText(InternalConfigStrings.getString("OpenLVLauncher.ToolTipClearButton"));
            final JMenuItem clearItem    = new JMenuItem("Clear");    //the menu start button

            /* Check that the user has an up to date JVM >= global var JVM_MINIMUM_REQ */
            String javaVersion = System.getProperty("java.version", "0.0");
	    thisComputerJava = javaVersion;

            String jvmBaseVersion = javaVersion.substring(0,javaVersion.indexOf(".") + 2);   //version string including one digit after the decimal eg 1.5
            double jvmBaseVersionNum = Double.parseDouble(jvmBaseVersion);
            myLogWriter.log("Java Version: " + jvmBaseVersion);
            if (jvmBaseVersionNum < JVM_MINIMUM_REQ)   //if JVM doesnt meet minimum requirements, alert user
            {
                Object[] options = {"Okay"};   //button text
                int answer = JOptionPane.showOptionDialog(frame, 
                        "Sorry, OpenLV Requires a More Recent Version of Java (" + JVM_MINIMUM_REQ + " or higher)" + endL + "Please Visit www.java.com/getjava to Download the Latest Version.",
                        "Detected an Old Version of Java: " + javaVersion,
                        JOptionPane.OK_OPTION,
                        JOptionPane.ERROR_MESSAGE,
                        null,              //no custom icon
                        options,           //the titles of buttons
                        options[0]);       //default button title

                if(answer == JOptionPane.OK_OPTION)   //user clicked ok button
                {
                    System.exit(1);      //close program
                }
            }



            if(isVMWare()){

		    vmWareInstallType   = getVMWareVersion();      //is the user using vmware server or vmware workstation


		    thisComputerVMWare = thisComputerVMWare + "(" + vmWareInstallType + ")";
		    isVMWareServer      = (vmWareInstallType == verServerOne || vmWareInstallType == verServerTwo)? true : false; 
		    VMWARE_VMRUN_PATH   = getVMWareVMRunPath(isVMWareServer);      //path to vmrun executable
		    myLogWriter.log("VMWare Install Type: " + vmWareInstallType);
		    /* Check that Some Version of VMWare is installed */
		    if (vmWareInstallType == verError ||   //if no vmware install (workstation or server) detected
			    vmWareInstallType == verServerTwo) //VMWare Server 2.x is not supported 
		    {
			String msg = "VMware Workstation 5.5+ or VMware Server 1.x is required to run OpenLV with VMWare" + endL + "Please visit www.vmware.com to download a copy and try again.";
			if ( vmWareInstallType == verServerTwo) {
			    msg = msg + "\n(Server 2.x is NOT supported at this time)";
			}
			Object[] options = {"Okay"};   //button titles
			int answer = JOptionPane.showOptionDialog(frame, 
				msg,
				"No VMWare installation detected",
				JOptionPane.OK_OPTION,
				JOptionPane.ERROR_MESSAGE,
				null,              //no custom icon
				options,           //the titles of buttons
				options[0]);       //default button title

			if(answer == JOptionPane.OK_OPTION)  //user clicked OK button
			{
			    System.exit(1);      //exit program
			} else if (answer == JOptionPane.CLOSED_OPTION){
			    //what about the escape key?!  answer is -1 (CLOSED_OPTION
			    System.exit(1);      //exit program
			}
		    }
	/*
		    if (vmWareInstallType == verWorkstationOld)
		    {
			String msg = "AN older version of VMware Workstation is installed.  Note that OpenLV only supports 5.5 or higher." + endL;
			Object[] options = {"Okay"};   //button titles
			int answer = JOptionPane.showOptionDialog(frame, 
				msg,
				"Old VMWare Workstation installation detected",
				JOptionPane.OK_OPTION,
				JOptionPane.ERROR_MESSAGE,
				null,              //no custom icon
				options,           //the titles of buttons
				options[0]);       //default button title

			if(answer == JOptionPane.OK_OPTION)  //user clicked OK button
			{
			    myLogWriter.log("Prompted 5.5+ check, user accepted.");
			} else if (answer == JOptionPane.CLOSED_OPTION){
			    //what about the escape key?!  answer is -1 (CLOSED_OPTION
			    System.exit(1);      //exit program
			}
		    }
	*/  
		    myLogWriter.log("VMWare Mount Path: " + VMWARE_MOUNT_PATH);
		    if (VMWARE_MOUNT_PATH == null)   //if no vmware-mount is detected
		    {
			Object[] options = {"Okay"};   //button titles
			int answer = JOptionPane.showOptionDialog(frame, 
				"vmware-mount.exe is required to run OpenLV.  VMWare's Virtual Disk Development Kit (VDDK) comes with a vmware-mount utility." + endL + 
				"Please visit VMWare's website to download a copy and try again." + endL +
				"Currently: http://www.vmware.com/download/sdk/virtualdisk.html (free registration required).",
				"No VMWare Disk Mount installation detected",
				JOptionPane.OK_OPTION,
				JOptionPane.ERROR_MESSAGE,
				null,              //no custom icon
				options,           //the titles of buttons
				options[0]);       //default button title

			if(answer == JOptionPane.OK_OPTION)  //user clicked OK button
			{
			    System.exit(1);      //exit program
			}
		    }


		    /* Checks For old VMWare Disk Mount Installations */
                    if(isWindows()){
			    if ((queryRegistryForOldVMMountPath() != null) )//|| (queryRegistryForServerVMMountPath() != null))   //if either old 5.5 style or newer 1.0.8; TMV new Server 1.0.8 doesn't have a reg value.  But also doesn't seem to cause an issue
			    {
				Object[] options = {"Okay"};   //button text
				int answer = JOptionPane.showOptionDialog(frame, 
					"An older version of VMWare's vmware-mount utility is installed on this system." + endL + 
					"For best results uninstall all versions of vmware-mount" + endL +
					"(including \"VMWare Diskmount Utility\" distributed as an add-on for Workstation 5.5)" + endL + 
					//        "and the optional install of \"VMWare Diskmount Utility\" with recent versions of Server 1.x)." + endL +
					"Then install the VMWare Virtual Disk Development Kit (VDDK) found on VMWare's website." + endL +
					"Currently: http://www.vmware.com/download/sdk/virtualdisk.html (free registration required).",
					"Detected an old version of vmware-mount",
					JOptionPane.OK_OPTION,
					JOptionPane.ERROR_MESSAGE,
					null,              //no custom icon
					options,           //the titles of buttons
					options[0]);       //default button title
				if(answer == JOptionPane.OK_OPTION)  //user clicked OK button
				{
				    //System.exit(1);      //exit program
				    myLogWriter.log("user was warned about old verions of vmware-mount installed.  continuing...");
				}
			    }
		    }
	    }
            if(isVirtualBox()){

		//TODO what to do with virutal box?
            }


            /* Start button click action */
            ActionListener startAListener = new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {            
                    startWasClicked = true;

                    final String sizeRamText        = sizeRamField.getText().trim();      //user input ram string
                    final String systemTimeText      = systemTimeField.getText().trim();      //user input sys time string
                    guestOSTypeText               = ((ComboBoxItem)osSelectionCombo.getSelectedItem()).getUnderlyingValue();

                    final String    mode            = modeGroup.getSelection().getActionCommand();   //generate | generate and launch

                    final String   bootSource         = bootSourceType.getSelection().getActionCommand();   //ImageFile | PhysicalDisk
                    /*##########start-gov##########*/
                    final boolean    clearPasswords      = clearPWBox.isSelected();      //blank out login passwords             
                    final boolean    clearDomainPasswords      = clearDomainPWBox.isSelected();      //blank out cached domain passwords             
                    final boolean    dumpHives         = dumpSAMBox.isSelected();      //dump sam/system hives to output dir    
                    myLogWriter.log("Clear Passwords: " + clearPasswords);
                    myLogWriter.log("Clear cached domain: " + clearDomainPasswords);
                    myLogWriter.log("Dump Hives: " + dumpHives); 
                    /*##########end-gov##########*/
                    final String    diskSize         = PARENT_DISK_SIZE_GB;         

                    long totalSectorsOnParentDisk = 0;         //sectors on disk image

                    final boolean isPhysicalDisk = bootSource.equalsIgnoreCase("PhysicalDisk") ? true : false;   //is the user booting a physical disk or a dd image

                    myLogWriter.log("Ram Size: " + sizeRamText);
                    myLogWriter.log("System Time: " + systemTimeText); 
		    //postOutput("System Time value" + systemTimeText);

                    myLogWriter.log("Guest OS: " + guestOSTypeText);
                    myLogWriter.log("Is Physical Disk: " + isPhysicalDisk);

                    final String physicalDiskName;
                    final String physicalDiskModel;
                    if(isPhysicalDisk)
                    {
                        physicalDiskName = ((ComboBoxItem)physicalDeviceCombo.getSelectedItem()).getUnderlyingValue();   // \\.\PhysicalDiskX
                        physicalDiskModel = ((ComboBoxItem)physicalDeviceCombo.getSelectedItem()).getDisplayText();   //physical disk model string
                    }
                    else
                    {
                        physicalDiskName = null;
                        physicalDiskModel = null;
                    }

                    final boolean isFullDisk;      //is the disk a full image (with mbr) or just bootable partition

                    final MasterBootRecord mbr;      //MBR for the disk image being booted

                    //start was clicked so disable it so it cannot be clicked repeatedly
                    Runnable updateStartButtonState = new Runnable() 
                    {
                        public void run() 
                        { 
                            start.setEnabled(false);      //disable start button
                            startItem.setEnabled(false);   //disable start menu item; 
                        }
                    };
                    SwingUtilities.invokeLater(updateStartButtonState);

                    //DateFormat formatter = DateFormat.getDateTimeInstance();//new SimpleDateFormat(dateFormat);
                    Date date = null;

                    try
                    {
                        try
                        {
                            //date = (Date)formatter.parse(systemTimeText);   //is user sys time input in proper format
                            date = formatter.parse(systemTimeText);   //is user sys time input in proper format
			    //postOutput("\ndate is" + date.getTime() + "\n");
                        }
                        catch(ParseException pe)
                        {
                            throw new OpenLVException("Invalid Date Format Entered: " + pe.getMessage() + endL + "Date Should Be In Format: " + FORMAT);
                        }
//                        final long userSysTimeSince1970 = date.getTime() / 1000;    //user specified seconds since January 1, 1970, 00:00:00 GMT
/*		 	SimpleDateFormat sdf = new SimpleDateFormat("MMM dd yyyy HH:mm:ss z");
			Date realDate = null;
			try{
		            realDate = sdf.parse("Dec 08 1979 23:10:00 CST");// should be 313521600
                        }
                        catch(ParseException pe)
                        {
                            throw new OpenLVException("Invalid Date Format Entered: " + pe.getMessage() + endL + "Date Should Be In Format: " + FORMAT);
                        }
			final long userSysTimeSince1970 = realDate.getTime();
		    postOutput("\nusersystimesince1970 " + userSysTimeSince1970 + "\n");
*/
			final long userSysTimeSince1970 = date.getTime() / 1000;
                        final long currentSysTimeSince1970 = now.getTime() / 1000;   //current secs since Jan 1, 1970

                        final String outDirVal   = directoryField.getText().trim();   //user specified output directory

                        final String[] pathForInputFiles = inputFileField.getText().trim().split("\\s*" + FILE_SEP_STRING + "\\s*");   //extract array of input file paths

                        sortChunkFileNamesByExtension(pathForInputFiles);   //sort the file extensions so they can be concatenated in order
                        myLogWriter.log("Sorted Input Files " + Arrays.toString(pathForInputFiles));

                        final boolean isChunked;
                        if(pathForInputFiles.length == 1)
                            isChunked = false;   //single file
                        else
                            isChunked = true;   //multiple chunked files   

                        //final String inFilePath   = inputFileField.getText().trim();

                        final File testDir = new File(outDirVal);   //test the output directory user input path to make sure it is an existing dir

                        if(!testDir.isDirectory())
                        {
                            throw new OpenLVException("Invalid input value: " + outDirVal + " does not exist or is not a directory");  
                        }

                        //check the validity of the input strings
                        try
                        {   
                            if(isPhysicalDisk && physicalDiskName == null)   //check if a valid physical disk was selected
                                throw new OpenLVException("No USB or Firewire phyical disks could be detected. Please make sure they are properly attached to your machine and try again.");

                            totalSectorsOnParentDisk = (long)(Float.parseFloat(diskSize) * BYTES_PER_GIG) / 512;

                            int i;
                            int lowerBoundRamSize = Integer.parseInt(InternalConfigStrings.getString("OpenLVLauncher.LowerBoundRamSizeMb"));
                            int upperBoundRamSize = Integer.parseInt(InternalConfigStrings.getString("OpenLVLauncher.UpperBoundRamSizeMb"));                  

                            //check if date is valid
                            if(userSysTimeSince1970 < 0 || userSysTimeSince1970 > currentSysTimeSince1970)
                            {
                                throw new OpenLVException("Date specified must be between Jan 1, 00:00:00 GMT 1970 and " + now);
                            }

                            //check size of ram input
                            i = Integer.parseInt(sizeRamText);
                            if(i < lowerBoundRamSize || i > upperBoundRamSize) 
                            {   
                                throw new OpenLVException("Invalid Memory Size: " + i + " Size should be between " + lowerBoundRamSize + "Mb and " +  upperBoundRamSize + "Mb");  
                            }

                            if(i % 4 != 0)   //vmware requires ram to be multiple of 4
                            {
                                throw new OpenLVException("Invalid Memory Size: " + i + " Size must be a multiple of 4");
                            }
                        }
                        catch(NumberFormatException nfe)
                        {
                            throw new OpenLVException("Invalid Input parameter(s) make sure you entered numerical values where applicable");
                        }

                        //for re-enabling the start button later on
                        final Runnable enableStartButtonState = new Runnable() 
                        {
                            public void run() 
                            { 
                                start.setEnabled(true);      //re-enable start button
                                startItem.setEnabled(true);   //re-enable start menu item   
                            }
                        };

                        //postOutput("vmware-mount at" + VMWARE_MOUNT_PATH + endL );

                        //check if the config file location for the vmrun executable is valid
                        if(isVMWare()){
				myLogWriter.log("vmrun path: " + VMWARE_VMRUN_PATH);
				File tempCheckVMRun = new File(VMWARE_VMRUN_PATH);
				if(!tempCheckVMRun.exists())
				{
				    throw new OpenLVException("Could Not Locate VMWare's vmrun executable on your system");
				}

				//make sure the mount drive letter is not already mounted from a previous run that did not properly clean up
				myLogWriter.log("Mount Drive Letter: " + MOUNT_DRIVE_LETTER);
				String mountDriveLetter = MOUNT_DRIVE_LETTER; 
				File testMountDriveLetter = new File(mountDriveLetter + ":");
				if(testMountDriveLetter.isDirectory())
				{
				    postOutput("Warning: Detected " + testMountDriveLetter + " is already in use, attempting to unmount" + endL);
				    boolean unmountSuccess = unmountSnapshot(mountDriveLetter, true);   //force unmount of disk image
				    if(unmountSuccess)
				    {
					postOutput("Successfully Cleaned Up, Continuing Normally" + endL);
				    }
				    else
				    {
					throw new OpenLVException("Could not unmount " + testMountDriveLetter + ": Perhaps it is being used by another device.");
				    }
				}

				if(isVMWareServer)
				    postOutput("Detected VMWare Server Installation" + endL);
				else
				    postOutput("Detected VMWare Workstation Installation" + endL);

				if(vmWareInstallType == -1)      //check if we found a valid vmware installation
				{
				    throw new OpenLVException("Error Detecting VMWare Installation. Make sure you have either VMware Server or Workstation installed on your system.");
				}
			}

                        MasterBootRecord tmp512;
                        File[] imgFiles = null;
                        File genericMBR = null; 
                        File customMBR = null;

                        if(!isPhysicalDisk)   //user chose to boot a disk image, not physical disk
                        {
                            //create array of files (chunks) for regular image it is just an array of length 1
                            imgFiles = new File[pathForInputFiles.length];
                            for(int i = 0; i < imgFiles.length; i++)
                                imgFiles[i] = new File(pathForInputFiles[i].trim());

                            for(int i = 0; i < imgFiles.length; i++)   //check if all input files exist
                            {
                                if(!imgFiles[i].exists())
                                {
                                    throw new OpenLVException("The image file: " + imgFiles[i].getName() + " could not be found" + imgFiles[i].getAbsolutePath());
                                }
                            }

                            //load first 512 bytes of image file supplied as an MBR to test its validity
                            tmp512 = new MasterBootRecord(imgFiles[0]);   //first img file should contain mbr
                        }
                        else   //physical disk
                        {
                            String deviceName = physicalDiskName;
                            int[] unsignedMBRBuffer = null;
                            try
                            {
                                RandomAccessFile raFile = new RandomAccessFile(deviceName, "r");
                                byte[] mbrBuffer = new byte[512];   //signed mbr bytes
                                raFile.readFully(mbrBuffer);   
                                raFile.close();
                                unsignedMBRBuffer = new int[512];   //unsigned bytes from mbr
                                for(int i = 0; i < 512; i++)   //convert signed bytes to unsigned
                                {
                                    unsignedMBRBuffer[i] = mbrBuffer[i];
                                    if(mbrBuffer[i] < 0)
                                        unsignedMBRBuffer[i] = 256 + mbrBuffer[i];
                                }
                            }
                            catch(FileNotFoundException fnf)
                            {
                                throw new OpenLVException("Could not open physical device: " + deviceName + " " + fnf.getMessage());
                            }
                            catch(IOException ioe)
                            {
                                postError("I/O problem reading physical device: " + deviceName + " " + ioe.getMessage());
                            }

                            tmp512 = new MasterBootRecord(unsignedMBRBuffer);
                        }

                        String imageNamePre;
                        if(isPhysicalDisk)
                            imageNamePre = physicalDiskModel;//physicalDiskName.substring(4,physicalDiskName.length());
                        else
                            imageNamePre = imgFiles[0].getName();

                        /* some physical devices may have a directory "/" in the name of the device (like write blocker combo dev)*/
                        /* we don't want that interpreted as a path */
                        imageNamePre = imageNamePre.replaceAll("\\\\", "_"); //just because
                        imageNamePre = imageNamePre.replaceAll("/", "_"); 
                        final String imageName = imageNamePre;

                        /* check for the MBR identifying sequence 55AA in bytes 510-511 of MBR as a check that it is valid*/
                        if(tmp512.getMarker()[0] != 0x55 || tmp512.getMarker()[1] != 0xAA) 
                        {
                            throw new OpenLVException("The image: " + imageName + " does not appear to be a disk file or bootable partition"
                                    + endL
                                    + "Please make sure that the image file(s) you chose is a valid disk image");
                        }
                        else   //we almost certainly have an mbr or a partition (not a garbage file)
                        {
                            myLogWriter.log("MBR Signature found: almost certainly have an mbr or partition (not garbagefile)");
                        }

                        //sanity check on user's image file selection
                        if(tmp512.isValidMBR())   //if mbr structure is a valid mbr (not first 512 bytes of a partition)
                        {
                            isFullDisk = true;   //full disk 
                            postOutput("Detected full disk image" + endL);
                        }
                        else 
                        {
                            isFullDisk = false;   //partition only
                            postOutput("Detected Partition Image" + endL);
                        }

                        if(isPhysicalDisk && !isFullDisk)   //physical partitions are not handled (eg mounting partition in PDE)
                        {
                            throw new OpenLVException("OpenLV cannot boot physical partitions. If you are using mounting software, make sure to mount the full disk image." + endL);
                        }

                        if(!isPhysicalDisk)
                        {
                            /* check if any files in the image are not readonly */
                            boolean writable = false;
                            for(int i = 0; i < imgFiles.length; i++)
                            {
                                if(imgFiles[i].canWrite())
                                {
                                    writable = true;
                                    myLogWriter.log("Writable File: " + imgFiles[i]);
                                }
                            }

                            if(writable)   //if any file is not read-only, prompt to make them read only
                            {
                                Object[] options = {"Yes", "No"};   //button titles
                                int answer = JOptionPane.showOptionDialog(frame, 
                                        "Making your image file read-only will provide an extra layer of\n protection against accidental modification of evidence\n Would you like to make the image file(s) read-only?",
                                        "The image you have chosen is not read-only",
                                        JOptionPane.YES_NO_OPTION,
                                        JOptionPane.WARNING_MESSAGE,
                                        null,              //no custom icon
                                        options,           //the titles of buttons
                                        options[0]);       //default button title

                                if(answer == JOptionPane.YES_OPTION)   //user wants to make file(s) read-only
                                {
                                    for(int i = 0; i < imgFiles.length; i++)   //set image files to read-only
                                        imgFiles[i].setReadOnly();
                                    postOutput("Making image file(s) read-only at user's request" + endL);
                                }      
                                else
                                    myLogWriter.log("User chose not to make image files read-only");
                            }
                        }

			//create secondary vmdks
			System.err.println("\n\n Creating secondary disks....\n");

			Iterator it=ddList.iterator();
			while(it.hasNext())
			{
				int i = 0;
				DiskData currentDisk = (DiskData)it.next();
				
				postOutput("processing data disk " + i++ + ": " + currentDisk + "\n");

				File fullOutVMDKPath = new File(testDir.getAbsolutePath().trim() + System.getProperty("file.separator"));// + outFileVMDKName;   //vmdk config
				currentDisk.createVMDKfile(fullOutVMDKPath);

			}
			//DDTODO

			//end secondary vmdks

                        /* Create the output files (vmx and vmdk) in the output directory (same base name as the image file)*/
                        String outFileVMXName          = imageName + ".vmx";
                        String outFileVMDKName          = imageName + ".vmdk";

                        //build name for new file with full path to output file
                        final String fullOutVMXPath    = testDir.getAbsolutePath().trim() + System.getProperty("file.separator") + outFileVMXName;      //vmx config
                        final String fullOutVMDKPath    = testDir.getAbsolutePath().trim() + System.getProperty("file.separator") + outFileVMDKName;   //vmdk config

                        File outVMXFile             = new File(fullOutVMXPath);            //vmx file 
                        File outVMDKFile             = new File(fullOutVMDKPath);         //vmdk file

                        //                  final int numExistingSnapshots = numSnapshots(fullOutVMXPath);
                        final int numExistingSnapshots;      //how many snapshots exist for this vmx already
                        if(snapshotExists(fullOutVMXPath,imageName))
                            numExistingSnapshots = 1;
                        else
                            numExistingSnapshots = 0;

                        myLogWriter.log("Num Existing Snapshots " + numExistingSnapshots);


                        if(numExistingSnapshots == -1)   //error detecting snapshots
                        {
                            throw new OpenLVException("Problem Detecting Number of Snapshots. Check that the path to the vmrun executable is configured correctly");
                        }

                        int answerContStartOver;      //user response to continue or start from scratch
                        final boolean startFromScratch;   

                        //if image has been launched before, prompt user to continue working or start over
                        if(numExistingSnapshots == 1)   //found existing snapshot (meaning this image was previusly launched)
                        {

                            //prompt user for "continue working" or "Start over"
                            Object[] options = {"Continue", "Start Over"};   //button titles
                            answerContStartOver = JOptionPane.showOptionDialog(frame, 
                                    "It appears that this disk image has been launched previously. \n Would you like to continue working where you left off or start from scratch?",
                                    "This image has been previously launched",
                                    JOptionPane.YES_NO_OPTION,
                                    JOptionPane.QUESTION_MESSAGE,
                                    null,              //no custom icon
                                    options,           //the titles of buttons
                                    options[0]);       //default button title


                            if(answerContStartOver == JOptionPane.NO_OPTION)   //user wants to start from scratch
                            {
                                String    prefixOfCurrentImage = imageName;
                                File[] filesInDir = testDir.listFiles();
                                String fName;
                                postOutput("User Chose To Start From Scratch" + endL);
                                for(int i = 0; i < filesInDir.length; i++)   //for each file in chosen directory
                                {
                                    fName = filesInDir[i].getName();
                                    boolean dontDelete = false;
                                    if(fName.startsWith(prefixOfCurrentImage) && !fName.endsWith(".mbr"))   //if prefix of file matches, delete it (dont delete .mbr which was created earlier) 
                                    {
                                        if(!isPhysicalDisk)   //images, not physical disk
                                        {
                                            for(int n = 0; n < imgFiles.length; n++)   //for all of the input image files
                                            {
                                                if(imgFiles[n].getName().compareTo(fName) == 0)   //file name matches one of the image files itself (we dont want to delete those)
                                                {
                                                    myLogWriter.log("Skipped: " + fName);
                                                    dontDelete = true;
                                                }
                                            }
                                        }
                                        if(!dontDelete)   //if current file in dir is not one of the image files
                                        {
                                            myLogWriter.log("Deleted: " + fName);
                                            filesInDir[i].delete();   //delete it
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


                        String fsType = null;      //type of filesystem for this image (NTFS, FAT, etc)
                        if(!isFullDisk) //partition only
                        {
                            //read in and modify generic mbr
                            long sizeOfPartition = 0;      //size of the partition in bytes
                            for(int i = 0; i < imgFiles.length; i++)
                            {
                                sizeOfPartition += imgFiles[i].length();
                            }
                            myLogWriter.log("Size of partition (bytes): " + sizeOfPartition);

                            //we cannot autodetect the os for partition images (because there is no MBR)
                            if(guestOSTypeText.equals("auto"))
                            {
                                throw new OpenLVException("OpenLV cannot auto detect the OS for partition images. Please select the image OS and try again.");
                            }

                            if(guestOSTypeText.equals("linux"))
                            {
                                throw new OpenLVException("OpenLV does not currently support linux partition images. If you have access to the full disk image, please select that and try again.");
                            }

                            //depending on the OS, choose the correct 'generic' mbr for the partition
                            if(guestOSTypeText.equals("win98") || guestOSTypeText.equals("winMe"))   //win 98 or Me
                            {
                                genericMBR = new File(InternalConfigStrings.getString("OpenLVLauncher.GenericMBRLocationW98Me"));
                                myLogWriter.log("Using Generic Windows MBR for 98/Me");   
                            }
                            else                     //non-win98/Me  //TODO linux???
                            {
                                genericMBR = new File(InternalConfigStrings.getString("OpenLVLauncher.GenericMBRLocation"));
                                myLogWriter.log("Using Generic Windows MBR for non-win98/me");
                            }

                            //mbr file based on the image file name with .mbr appended to it
                            customMBR = new File(testDir.getAbsolutePath().trim() + 
                                    System.getProperty("file.separator") + 
                                    imgFiles[0].getName() + 
                                    ".mbr");

                            //note, this does not write the nt driver serial number (bytes 440-443 of mbr)
                            //this is because we need the vmdk to mount the disk to check the registry for this
                            //serial number but the vmdk is not created at this point. The serial number is added after the vmdk is created

                            if(startFromScratch)//only modify mbr if user wants to start from scratch (otherwise it was previously created)
                                modifyGenericMBR(sizeOfPartition, 255, genericMBR, customMBR);      //creates customMBR
                            mbr = new MasterBootRecord(customMBR);   //use modified generic mbr             
                        }
                        else   //full disk
                        {
                            if(!isPhysicalDisk)
                                mbr = new MasterBootRecord(imgFiles[0]);   //use mbr from image
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

                        myLogWriter.log("MBR Info:");
                        myLogWriter.log(mbr.toString());   //log MBR contents to file

                        if(!guestOSTypeText.equals("auto"))   //if the user selected the OS
                        {
                            //sanity check on user's OS type selection
                            if(OperatingSystem.isNTKernel(guestOSTypeText) && mbr.getBootablePartition().isNotWindowsBased())   //selected NT based sys, AND mbr has non windows FS
                            {
                                throw new OpenLVException("You have selected a Windows NT Based OS, but the bootable partition's filesystem does not appear to be compatible with NT Based Systems. Please select the correct OS for your disk image and try again.");
                            }
                        }

                        if(startFromScratch)   //only generate the vmx and vmdk if the user wants to start from scratch (otherwise it is already there)
                        {
                            //check if the vmx output file already exists
                            if(!outVMXFile.exists())
                            {
                                try   //no, so create it
                                {  
                                    outVMXFile.createNewFile();   //create the file
                                    myLogWriter.log("Created: " + outVMXFile.getAbsolutePath());

                                }
                                catch(IOException ioe)
                                {
                                    throw new OpenLVException("Could not create file: " + outFileVMXName + " in " + outDirVal + ioe.getMessage());  
                                }
                            }
                            else if(!outVMXFile.canWrite()) //check if the file is writable
                            {
                                throw new OpenLVException(outFileVMXName + " in " + outDirVal + " is not writable."
                                        + endL
                                        + "Please make the file writable or choose a new one.");  
                            }

                            //vmx file exists and is writable, so write it
                            PrintWriter vmxWriter = null;
                            try
                            {
                                StringBuffer vmxBuffer = new StringBuffer();
                                postOutput("Generating vmx file..." + endL);
                                vmxWriter = new PrintWriter(new BufferedWriter( new FileWriter(outVMXFile) ));
                                vmxBuffer.append("#Static Values" + endL);
                                vmxBuffer.append("config.version = \"8\"" + endL);
                                vmxBuffer.append("virtualHW.version = \"3\"" + endL);
                                vmxBuffer.append("floppy0.present = \"FALSE\"" + endL);
                                vmxBuffer.append("displayName=\"" + imageName + "\"" + endL);

                                vmxBuffer.append(endL);

                                vmxBuffer.append("#Drive Info" + endL);
                                vmxBuffer.append("ide0:0.present = \"TRUE\"" + endL);

                                if(numExistingSnapshots > 0 && answerContStartOver == JOptionPane.YES_OPTION)   //if snapshots exist already and the user wants to Continue with what they were working on, point the filename to the snapshot
                                    vmxBuffer.append("ide0:0.fileName = \"" + fullOutVMDKPath.substring(0,fullOutVMDKPath.length()-5).concat("-000001.vmdk") + "\"" + endL);
                                else                     //otherwise point the filename to the regualar vmdk
                                    vmxBuffer.append("ide0:0.fileName = \"" + fullOutVMDKPath + "\"" + endL);

                                vmxBuffer.append("ide0:0.deviceType = \"disk\"" + endL);
                                vmxBuffer.append("ide0:0.mode = \"persistent\"" + endL);

                                vmxBuffer.append("ide1:0.present = \"TRUE\"" + endL);
                                vmxBuffer.append("ide1:0.fileName = \"auto detect\"" + endL);
                                vmxBuffer.append("ide1:0.deviceType = \"cdrom-raw\"" + endL);
				int disksAdded = 2;  //two IDE slots taken (main disk and cdrom) each primary on ide channel 0 and 1 respectively
						     //this could obviously be a 2d array, or a class, etc, but there are only 4 values so just simple...

				//need to process all data disks  TODO FIXME

				Iterator dit=ddList.iterator();
				int scsiid = 0;
				while(dit.hasNext())
				{
					int i = 0;
					int channel = 0;
					int devid = 0;
					DiskData currentDisk = (DiskData)dit.next();
					String deviceTypeString = "ide";
					
					postOutput("adding data disk " + i++ + " to VM...\n");
					if(disksAdded < 4)  //disks 0-3 added as IDE
					{
						deviceTypeString = "ide";
					}
					else
					{
						deviceTypeString = "scsi";
					}
					if(disksAdded == 2){
						channel = 0;
						devid = 1;
					}
					else if(disksAdded == 3){
						channel = 1;
						devid = 1;
					}
					else if(disksAdded >= 4){
						channel = 0;
						devid = 1;
					}
					else {
						channel = 0;		//TODO how many scsi devices available per channel?  do i need to skip id 7?
						devid = scsiid++;
					}

					vmxBuffer.append(endL);

					vmxBuffer.append("#Data Disk " + i + endL);
					vmxBuffer.append(deviceTypeString + channel + ":" + devid +".present = \"TRUE\"" + endL);
					String outVMDKPath = currentDisk.getFilename();

					if(numExistingSnapshots > 0 && answerContStartOver == JOptionPane.YES_OPTION)   //if snapshots exist already and the user wants to Continue with what they were working on, point the filename to the snapshot
					    vmxBuffer.append(deviceTypeString + channel + ":" + devid +".fileName = \"" + outVMDKPath.substring(0,outVMDKPath.length()-5).concat("-000001.vmdk") + "\"" + endL);
					else                     //otherwise point the filename to the regualar vmdk
					    vmxBuffer.append(deviceTypeString + channel + ":" + devid +".fileName = \"" + outVMDKPath + "\"" + endL);

					vmxBuffer.append(deviceTypeString + channel + ":" + devid +".deviceType = \"disk\"" + endL);
					vmxBuffer.append(deviceTypeString + channel + ":" + devid +".mode = \"persistent\"" + endL);

					disksAdded++;


					//File fullOutVMDKPath = new File(testDir.getAbsolutePath().trim() + System.getProperty("file.separator"));// + outFileVMDKName;   //vmdk config
					//currentDisk.createVMDKfile(fullOutVMDKPath);

				}

                                vmxBuffer.append(endL);

                                vmxBuffer.append("#User Specified" + endL);
                                vmxBuffer.append("memsize=\"" + sizeRamText + "\"" + endL);
                                vmxBuffer.append("rtc.starttime=\"" + userSysTimeSince1970 + "\"" + endL);
                                //postOutput("rtc.starttime=\"" + userSysTimeSince1970 + "\"" + endL);
                                vmxBuffer.append("tools.syncTime=\"FALSE\"" + endL);
                                vmxBuffer.append("time.syncronized.continue=\"FALSE\"" + endL);
                                vmxBuffer.append("time.syncronized.restore=\"FALSE\"" + endL);
                                vmxBuffer.append("time.syncronized.resume.disk=\"FALSE\"" + endL);
                                vmxBuffer.append("time.syncronized.resume.memory=\"FALSE\"" + endL);
                                vmxBuffer.append("time.syncronized.shrink=\"FALSE\"" + endL);
                                vmxBuffer.append("time.syncronized.tools.startup=\"FALSE\"" + endL);
                                vmxBuffer.append("isolation.tools.setOption.disable=\"TRUE\"" + endL);

                                if(!guestOSTypeText.equals("auto"))   //if the user chose an OS manually from dropdown (not auto detect)
                                    vmxBuffer.append("guestOS=\"" + guestOSTypeText + "\"" + endL);

                                if(!isVMWareServer)   //disable snapshots on vmware workstation to prevent accidental modification of original image -- vmware server does not have the snapshot tree so only necessary for workstation
                                    vmxBuffer.append("snapshot.disabled = \"TRUE\"");

                                myLogWriter.log(vmxBuffer.toString() + endL + endL );   //log the contents of the vmx
                                vmxWriter.write(vmxBuffer.toString());   //write the buffer to the vmx file

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
			    //VMX is "done" (VMWare vm is created)

			    final String basePath = testDir.getAbsolutePath().trim(); // + System.getProperty("file.separator")			
			    if(isVirtualBox()){
				if(!createVirtualBox(imageName,guestOSTypeText,basePath)){
					postError("create vmbox vm error" + imageName + " " + guestOSTypeText + " " + basePath);
					System.exit(1);  //TODO instead of exiting, should leave the GUI open
				}
				else
				{
					postOutput("VirtualBox vm created");
				}
				if(!addSettingsToVirtualBox(imageName, sizeRamText)){
					postError("add vm settings error" + imageName + " " + sizeRamText );
					System.exit(1);  //TODO instead of exiting, should leave the GUI open
				}
				else
				{
					postOutput("VirtualBox settings changed");
				}
				
			    }

                            //check if the vmdk output file exists
                            if(!outVMDKFile.exists())
                            {
                                try   //no, so create the file
                                {  
                                    outVMDKFile.createNewFile();   //create the file
                                    myLogWriter.log("Created: " + outVMDKFile.getAbsolutePath());
                                }
                                catch(IOException ioe)
                                {
                                    throw new OpenLVException("Could not create file: " + outFileVMDKName + " in " + outDirVal
                                            + endL
                                            + ioe.getMessage());  
                                }
                            }
                            else if(!outVMDKFile.canWrite()) //check if the file is writable
                            {
                                throw new OpenLVException(outFileVMDKName + " in " + outDirVal + " is not writable."
                                        + endL
                                        + "Please make the file writable or choose a new one.");  
                            }

                            //vmdk file exists and is writable, so write it
                            PrintWriter vmdkWriter = null;
                            try
                            {
                                StringBuffer vmdkBuffer = new StringBuffer();
                                postOutput("Generating vmdk file..." + endL);
                                vmdkWriter = new PrintWriter(new BufferedWriter( new FileWriter(outVMDKFile)));
                                vmdkBuffer.append("# Disk Descriptor File" + endL);
                                vmdkBuffer.append("version=1" + endL);
                                vmdkBuffer.append("CID=fffffffe" + endL);
                                vmdkBuffer.append("parentCID=ffffffff" + endL);

                                if(!isPhysicalDisk)   //image file(s)
                                    vmdkBuffer.append("createType=\"monolithicFlat\"" + endL);
                                else   //physical disk
                                    vmdkBuffer.append("createType=\"fullDevice\"" + endL);

                                vmdkBuffer.append(endL);

                                vmdkBuffer.append("# Extent description" + endL);

                                long unallocatedSpace;
                                if(!isFullDisk)   //just a partition image
                                {
                                    if(customMBR != null)
                                    {
                                        vmdkBuffer.append("RW 63 FLAT \"" + customMBR + "\" 0" + endL);   //add reference to mbr so we can boot partition
                                    }
                                    else
                                        throw new OpenLVException("Custom MBR not found");

                                    if(isChunked)   //chunked partition image
                                    {
                                        long sectorsInChunk;
                                        for(int i = 0; i < imgFiles.length; i++)
                                        {
                                            sectorsInChunk = imgFiles[i].length() / 512;   //number of sectors in current chunk
                                            vmdkBuffer.append("RW " + (sectorsInChunk) + " FLAT " + "\"" + imgFiles[i].getCanonicalPath() + "\"" + " 0" + endL);   //one line for each chunk for extent description
                                        }

                                        unallocatedSpace = totalSectorsOnParentDisk - mbr.totalSectorsFromPartitions() - 63;   
                                    }
                                    else   //not a chunked partition image
                                    {
                                        vmdkBuffer.append("RW " + (mbr.totalSectorsFromPartitions()) + " FLAT " + "\"" + imgFiles[0] + "\"" + " 0" + endL);   //just need one extent line pointing to whole image file
                                        unallocatedSpace = totalSectorsOnParentDisk - mbr.totalSectorsFromPartitions() - 63;   
                                    }
                                }
                                else         //full disk image
                                {
                                    if(isChunked)   //chunked full disk image
                                    {
                                        long sectorsInChunk = 0, totalSectors = 0;
                                        for(int i = 0; i < imgFiles.length; i++)
                                        {
                                            sectorsInChunk = imgFiles[i].length() / 512;
                                            totalSectors += sectorsInChunk;
                                            vmdkBuffer.append("RW " + (sectorsInChunk) + " FLAT " + "\"" + imgFiles[i].getCanonicalPath() + "\"" + " 0" + endL);   //one line describing each chunk for the extent description
                                        }
                                        if(totalSectors >= mbr.totalSectorsFromPartitions())
                                            unallocatedSpace = totalSectors - mbr.totalSectorsFromPartitions() - 63;   //standard way to get unallocated space (sectors in file - sectors in mbr)
                                        else
                                            unallocatedSpace = mbr.totalSectorsFromPartitions() - totalSectors + 63; //added because sometimes total sectors making up file is less than total in mbr for partitions (eg nist image) so here we account for unallocated
                                    }
                                    else   //not a chunked full disk image
                                    {
                                        if(isPhysicalDisk)
                                        {
                                            vmdkBuffer.append("RW " + (mbr.totalSectorsFromPartitions() + 63) + " FLAT " + "\"" + physicalDiskName + "\"" + " 0" + endL);   //just need one extent line pointing to the physical disk
                                            unallocatedSpace = mbr.totalSectorsFromPartitions()/1000;   //fudge factor - since total sectors from file shows up as 0 for physical disks
                                        }
                                        else   //full disk dd image
                                        {
                                            vmdkBuffer.append("RW " + (mbr.totalSectorsFromPartitions() + 63) + " FLAT " + "\"" + imgFiles[0] + "\"" + " 0" + endL);   //just need one extent line pointing to whole image file
                                            unallocatedSpace = mbr.totalSectorsOnDiskFromFile() - mbr.totalSectorsFromPartitions() /*- mbr.getBootablePartition().getEndSector()*/ + 63;   //add 63?
                                        }
                                    }
                                }

                                if(unallocatedSpace > 0)
                                    vmdkBuffer.append("RW " + unallocatedSpace + " ZERO" + endL);   //leftover 0'ed space

                                vmdkBuffer.append(endL);

                                vmdkBuffer.append("#DDB - Disk Data Base" + endL);
                                vmdkBuffer.append("ddb.adapterType = \"ide\"" + endL);
                                vmdkBuffer.append("ddb.geometry.sectors = \"" + mbr.getBootablePartition().getEndSector() + "\"" + endL);
                                vmdkBuffer.append("ddb.geometry.heads = \"" + mbr.getBootablePartition().getEndHead() + "\"" + endL);
                                vmdkBuffer.append("ddb.geometry.cylinders = \"" + mbr.largestCylinderValOnDisk() + "\"" + endL);
                                vmdkBuffer.append("ddb.virtualHWVersion = \"3\"" + endL);

                                myLogWriter.log(vmdkBuffer.toString());
                                vmdkWriter.write(vmdkBuffer.toString());   //write the vmdk buffer to the file
                            }
                            catch(IOException ioe)
                            {
                                throw new OpenLVException("Error writing vmdk file: " + outVMDKFile.getAbsolutePath());
                            }
                            finally 
                            {
                                if (vmdkWriter != null) 
                                    vmdkWriter.close();
                            }   
                        }

			//creation of virtual disk, the vmdk is done
			if(isVirtualBox()){
                                if(!addControllerToVirtualBox(imageName)){
                                        postError("add controller vmbox vm error" + imageName );
                                        System.exit(1);  //TODO instead of exiting, should leave the GUI open
                                }
                                if(!addVmdkToVirtualBox(outVMDKFile.getAbsolutePath(), imageName)){
                                        postError("add disk vmbox error" + imageName + " " + outVMDKFile.getAbsolutePath());
                                        System.exit(1);  //TODO instead of exiting, should leave the GUI open
                                }
                                else
                                {
                                        postOutput("vmdk added to VirtualBox ");
                                }

                            }


                        final File custMBR = customMBR;   //for access inside swingworker -- should probably change this
                        final String fileSysType = fsType;   //for access inside swingworker

                        //share cpu time with GUI so it remains responsive during time consuming processes
                        worker = new SwingWorker() 
                        {
                            public Object construct() 
                            {
                                boolean prepWorked = true; //did preparation for vm launch work
				boolean gotAtLeastOneOS = false;

                                String mountDriveLetter = MOUNT_DRIVE_LETTER; 
                                int bootablePartitionIndex = mbr.getBootablePartitionIndex();

                                OperatingSystem[] osArr = {null,null,null,null};
                                if(numExistingSnapshots == 0 || startFromScratch)   //no snapshots already created or user chose to start from scratch
                                {
                                    //TODO prepare all bootable partitions for launch (check each of the four entries)

                                    //prepare the bootable partition for launch
                                    osArr = prepareVMForLaunch(fullOutVMXPath, fullOutVMDKPath, mountDriveLetter, 
                                            guestOSTypeText, fileSysType, isFullDisk, bootablePartitionIndex,
                                            clearPasswords, clearDomainPasswords, dumpHives, /*#####Gov#####*/ 
                                            testDir.getAbsolutePath().trim(), imageName, frame);

			            for(int part =1; part <=4 ; part++){
					    if(osArr[part-1] != null)
					    {
						guestOSTypeText = osArr[part-1].getVmGuestOS();
						gotAtLeastOneOS = true;

					    //get find first nt partition on the disk (if there is one)
					    //int ntPartitionIndex = bootablePartitionIndex;

					    //write the nt drive serial number to the customized mbr from above
					    if(!isFullDisk && OperatingSystem.isNTKernel(guestOSTypeText)) //dealing with just an NTKernel partition 
					    {
						int[] ntDriveSerialNum = {0x00, 0x00, 0x00, 0x00}; //generic 4 byte serial for non nt systems (those done require serial numbers)

						if(osArr[part-1].getPartitionID() - 1 > 0)   //if os is nt based (but not Original NT)
						{
						    String vmdkSnapshotLoc = fullOutVMDKPath.substring(0,fullOutVMDKPath.length()-5).concat("-000001.vmdk");   //xyz.vmdk -> xyz-000001.vmdk  (snapshot naming convention)
						    myLogWriter.log("Snapshot Location: " + vmdkSnapshotLoc);

						    if(startFromScratch)   //if user wants to start from scratch
							ntDriveSerialNum = getNTDriveSerialNum(vmdkSnapshotLoc, false, osArr[part-1], osArr[part-1].getPartitionID()-1);   //get the NT drive serial number
							//ntDriveSerialNum = getNTDriveSerialNum(vmdkSnapshotLoc, false, osArr[part-1], ntPartitionIndex);   //get the NT drive serial number
						    else
							ntDriveSerialNum = getNTDriveSerialNum(vmdkSnapshotLoc, true, osArr[part-1], osArr[part-1].getPartitionID()-1);   //get the NT drive serial number

						    myLogWriter.log("Drive Serial Number: " + Arrays.toString(ntDriveSerialNum));

						}

						if(ntDriveSerialNum != null)   //if we got the 4 byte serial number, write it to the mbr
						{
						    try 
						    {
							RandomAccessFile raf = new RandomAccessFile(custMBR, "rw");
							myLogWriter.log("MBR File: " + custMBR.getName() + " opened r/w");

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
						else   //error retrieving drive serial
						    prepWorked = false;
					    }
					}
				    }
				    if(gotAtLeastOneOS){
			        	postOutput("VM prepared for launch.  (bootpartition: " + bootablePartitionIndex + ")" + endL);
			            }
			            else
			            {
				        postError("Problem preparing VM." + endL);
				        prepWorked = false;
			            }
				    
                                }   
                                else if(numExistingSnapshots == 1)   //user wants to continue with existing snapshot
                                {
                                    postOutput("Using Existing Snapshot" + endL);
                                }
                                else
                                {
                                    postError("Existing Snapshot Detection Failure: " + numExistingSnapshots);
                                    prepWorked = false;
                                }

                                if(mode.equals("GenerateAndLaunch")) //did the user choose to launch the vm as well
                                {
                                    if(prepWorked)   //if VM Launch preparation worked
                                    {   
					if(isVMWare()){                         
						if(!isVMWareServer)   //VMWare Workstation
						{
						    boolean started = startVMProc(VMWARE_VMRUN_PATH, //vmrun
							    "start", 
							    fullOutVMXPath
							    );            //launch the vm image process
						    if(started)                                
							postOutput("VMWare Workstation Launch Completed" + endL);
						    else
							postError("VMWare Workstation Launch Failed");
						}
						else            //VMWare Server
						{                                      
						    boolean consoleLaunched = startVMWareServerConsole(fullOutVMXPath);   //start the console so the vm GUI is visible (otherwise the vm runs in the background)

						    if(consoleLaunched)   //console successfully opened
						    {
							postOutput("VMWare Console Started" + endL);
							//start the image in the console
							boolean started = startVMProc(VMWARE_VMRUN_PATH,       //vmrun
								"start", 
								fullOutVMXPath
								);            //launch the vm image process

							if(started)   
							    postOutput("Image Sucessfully Launched" + endL);
							else
							    postOutput("VMware Server Launch Failed");

						    }
						    else
							postOutput("VMware Server Console Could Not Be Started" + endL);
						}
					}
					else if(isVirtualBox()){
						startVirtualBox(imageName);
						postOutput("Launching virtualbox");
					}
					else{
						postError("can't find virtualization product");
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
                    catch(OpenLVException lve)      //catch the launch errors
                    {
                        postError(lve.getMessage());
                        postError("Image could not be launched in the VM."); 

                        //for re-enable the start buttons
                        final Runnable reEnableStartState = new Runnable() 
                        {
                            public void run() 
                            { 
                                start.setEnabled(true);      //re-enable start button
                                startItem.setEnabled(true);   //re-enable start menu item   
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
                myLogWriter.log("User Closed Program Window");
                stopProc();
                myLogWriter.log("Stopped running processes");
                cleanUp();
                myLogWriter.log("Cleaned Up");
                System.exit(0);
            }
            });

            /* Browse For Output Location Group */
            JPanel browseOutPanel = new JPanel();
            browseOutPanel.add(directoryField);
            browseOutPanel.add(browseOutputButton);
            browseOutPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createBevelBorder(2),InternalConfigStrings.getString("OpenLVLauncher.VMOutputDirBorderText"))); 

            /* Browse For Input Location Group */
            JPanel browseInPanel = new JPanel();   //1 col, unlimited rows
            browseInPanel.setLayout(new BoxLayout(browseInPanel,BoxLayout.PAGE_AXIS));
            browseInPanel.add(bootSourcePanel);
            //       browseInPanel.add(inTextAndButton);
            //       browseInPanel.add(physicalDeviceSelectionPanel);
            browseInPanel.add(bootSourceCardPanel);

            browseInPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createBevelBorder(2),InternalConfigStrings.getString("OpenLVLauncher.ForensicImageBorderText"))); 

            /* Action Button Group */
            JPanel actionPanel = new JPanel();
            actionPanel.add(start);
            actionPanel.add(clear);
            actionPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createBevelBorder(2),"Actions")); 


            /* Group the label/textbox panels together into a larger input panel */
            JPanel inputPanelTop = new JPanel(new GridLayout(0,2));   //2 cols, as many rows as needed
            inputPanelTop.add(sizeRamPanel);
            inputPanelTop.add(systemTimePanel);
            inputPanelTop.add(osSelectionPanel);
            inputPanelTop.add(samPanel);   /*#####Gov#####*/ 

            JPanel inputPanel = new JPanel(new BorderLayout(0,0));
            inputPanel.add(inputPanelTop);      //all regular input

            inputPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createBevelBorder(2),InternalConfigStrings.getString("OpenLVLauncher.InputParamBorderText"))); 

            Container mainContainer = frame.getContentPane();
            mainContainer.setLayout(new BoxLayout(mainContainer, BoxLayout.PAGE_AXIS));
            mainContainer.add(inputPanel);
            mainContainer.add(browseInPanel);

            JPanel optionPanel = new JPanel(new GridLayout(2,1)); //group action + browse(in/out) together
            //       optionPanel.add(browseInPanel);
            optionPanel.add(browseOutPanel);
            optionPanel.add(modePanel);
            mainContainer.add(optionPanel);

            mainContainer.add(actionPanel);
            mainContainer.add(outPanel);

            //build the menus
            JMenuBar menuBar = new JMenuBar();      //create a menu bar to hold the menus
            JMenu fileMenu = new JMenu("File");      //file menu is the main menu
            fileMenu.setMnemonic(KeyEvent.VK_F);   //allow for alt-f to access file menu


            JMenu helpMenu = new JMenu("Help");
            JMenu advancedMenu = new JMenu("Edit");

            //set up the actions sub menu
            JMenu actionsSubMenu = new JMenu("Actions");
            startItem.addActionListener(startAListener);
            clearItem.addActionListener(clearAListener);
            actionsSubMenu.add(startItem);
            actionsSubMenu.add(clearItem);

            fileMenu.add(actionsSubMenu);   //add the actions submenu to the file parent menu

            JMenuItem aboutMenuItem = new JMenuItem("About");
            aboutMenuItem.addActionListener(new ActionListener()
                    {
                        public void actionPerformed(ActionEvent e)
            {
                Runnable doShowAboutDialog = new Runnable() 
            {
                public void run() 
            {

		    Long installedMemoryAmount = getInstalledMemory();
		    thisComputerRAM = "" + installedMemoryAmount/1024/1024;
		    final String thisComputerVersion = //getSystemProps() +
			   "\nDetected host properties:" +
			   "\nVMWare: " + thisComputerVMWare + 
			   "\nRAM:    " + thisComputerRAM + "+ MB"+
			   "\nOS:     " + thisComputerOS  + " " + thisComputerSP +
			   "\nArch:   " + thisComputerArch +
			   "\nJava:   " + System.getProperty("java.vendor") + " " + thisComputerJava +
			   "\nJVM:    " + System.getProperty("java.vm.vendor") + " " + System.getProperty("java.vm.version");

                String AboutText = InternalConfigStrings.getString("OpenLVLauncher.AboutBoxText") + thisComputerVersion;
                JOptionPane.showMessageDialog(frame,AboutText,"About This Program",JOptionPane.INFORMATION_MESSAGE);
            }
            };
            SwingUtilities.invokeLater(doShowAboutDialog);
            }
            });
            helpMenu.add(aboutMenuItem);

            JMenuItem helpPackMenuItem = new JMenuItem("CreateHelpArchive");
            helpPackMenuItem.addActionListener(new ActionListener()
                    {
                        public void actionPerformed(ActionEvent e)
            {
                Runnable doShowHelpPackDialog = new Runnable() 
            {
                public void run() 
            {
		String[] filenames = new String[]{
			PermLogFile, 
		    	TempLogFile,
		    	jarFile,
		        confFile
			};

		byte[] buf = new byte[1024];
	        ZipFileMenu zfm = new ZipFileMenu(frame,new File("c:\\"));  //TODO FIXME needs to be non-constant (should be outDirVal probably)
	        ZipData myZipData = zfm.showDialog();
	        if(myZipData.isValid())
	        {
			/*
		    postOutput("Select zip output dir: \n" + myZipData + "\n");
		    postOutput("Select zip Contact Name : \n" + myZipData.getContactName() + "\n");
		    postOutput("Select zip Email : \n" + myZipData.getContactEmail() + "\n");
		    postOutput("Select zip Message: \n" + myZipData.getHelpMessage() + "\n");
		    */


			try {
			    Long installedMemoryAmount = getInstalledMemory();
			    thisComputerRAM = "" + installedMemoryAmount/1024/1024;
			    final String thisComputerVersion = //getSystemProps() +
				   "\nDetected host properties:" +
				   "\nVMWare: " + thisComputerVMWare + 
				   "\nRAM:    " + thisComputerRAM + "+ MB"+
				   "\nOS:     " + thisComputerOS  + " " + thisComputerSP +
				   "\nArch:   " + thisComputerArch +
				   "\nJava:   " + System.getProperty("java.vendor") + " " + thisComputerJava +
				   "\nJVM:    " + System.getProperty("java.vm.vendor") + " " + System.getProperty("java.vm.version");

			    String outFilename = "HelpArchive.zip";
			    File outFile = new File(myZipData.getFilePath(),outFilename);
			    try{
				    if(outFile.exists()){
				      Object[] options = {"Okay","Cancel"};   //button titles
					    int answer = JOptionPane.showOptionDialog(frame, 
						    "A help file exists at the location you specified, if you continue that file will be overwritten!" + endL +
						    "Are you sure you want to continue?",
						    "Are you sure!?",
						    JOptionPane.OK_OPTION,
						    JOptionPane.ERROR_MESSAGE,
						    null,              //no custom icon
						    options,           //the titles of buttons
						    options[0]);       //default button title

					    if(answer != JOptionPane.OK_OPTION)   //user clicked ok button
					    {
						return ;
					    }

				    }
			    }catch(Exception e){
				    postError("zip file output check failure: " + e.getMessage());
			    }
			

			    ZipOutputStream out = new ZipOutputStream(new FileOutputStream(outFile));

			    for (int i=0; i<filenames.length; i++) {
				FileInputStream in = new FileInputStream(filenames[i]);

				// this is done so that the full path isn't embedded in the zip
				File tempFile = new File(filenames[i]);
				String zipEntName = tempFile.getName();

				ZipEntry zEnt = new ZipEntry(zipEntName);
				//zEnt.setTime()
				out.putNextEntry(zEnt);

				int len;
				while ((len = in.read(buf)) > 0) {
				    out.write(buf, 0, len);
				}
				out.closeEntry();
				in.close();

			    }
			   out.putNextEntry(new ZipEntry("metainfo"));
			   out.write(("created: " + formatter.format(new Date()) + endL).getBytes());
			   out.write(("Name: " + myZipData.getContactName() + endL).getBytes());
			   out.write(("Email: " + myZipData.getContactEmail() + endL).getBytes());
			   out.write(("Help: " + myZipData.getHelpMessage() + endL).getBytes());
			   out.write(thisComputerVersion.getBytes());
			   out.closeEntry();


			    out.close(); //close the zip file
			} catch (IOException e) {
				postError("zip file creation failed" + e.getMessage());
			}
			postOutput("zip file created at " + myZipData.getFilePath());
			JOptionPane.showMessageDialog(frame,"File created!","Help Archive Created",JOptionPane.INFORMATION_MESSAGE);
	        }
	        else
	        {
		    postOutput("Zip data provided is invalid\n");
	        }
		    

            }
            };
            SwingUtilities.invokeLater(doShowHelpPackDialog);
            }
            });
            helpMenu.add(helpPackMenuItem);

	    if(Desktop.isDesktopSupported()){
		    final Desktop desktop = Desktop.getDesktop();
		    if(desktop.isSupported(Desktop.Action.EDIT)){
            /* Edit Config File*/
            JMenuItem editMenuItem = new JMenuItem("Configure");
            editMenuItem.addActionListener(new ActionListener()
                    {
                        public void actionPerformed(ActionEvent e)
            {
		    File file = new File(confFile);
		    if(!file.exists()){
			    try{
				    postError("file not found, creating " + confFile + "...");
				    file.createNewFile();
				    FileOutputStream out = new FileOutputStream(file);
				    PrintStream p = new PrintStream(out);
				    p.println(ConfText.theText);
				    out.close();
			    }
			    catch(Exception exc){
				postError("Unable to create file. \n" + exc.toString());
			    }
		    }
		    try {
			    desktop.edit(file);  //reload items XXXX
		    }
		    catch(Exception exc){
			postError("Unable To Launch Editor. Try Editing it manually in your user home directory. \n" + exc.toString());
		    }
            }   
            });
            fileMenu.add(editMenuItem);
		    }
	    }


            /* Help Menu Item */
            JMenuItem helpMenuItem = new JMenuItem("Help");
            helpMenuItem.addActionListener(new ActionListener()
                    {
                        public void actionPerformed(ActionEvent e)
            {
                if(!launchHelpDoc(InternalConfigStrings.getString("OpenLVLauncher.HelpDocLocation")))
                postError("Unable To Launch Help Document. Try Opening it Manually From The OpenLV Installation Directory.");
            }   
            });
            helpMenu.add(helpMenuItem);

            /* Separator */
            fileMenu.addSeparator();   

            /* Exit Menu Item */
            JMenuItem exitMenuItem = new JMenuItem("Exit");
            exitMenuItem.addActionListener(new ActionListener()
                    {
                        public void actionPerformed(ActionEvent e)
            {
                stopProc();      //stop the running process (if running)
                cleanUp();      //clean up by unloading reg/unmounting disk etc
                System.exit(1);   //exit program
            }
            });

            fileMenu.add(exitMenuItem);

	    JMenuItem secondaryDiskMenuItem = new JMenuItem("Add Data Disk");
	    secondaryDiskMenuItem.addActionListener(new ActionListener()
		    {
			    public void actionPerformed(ActionEvent e)
	    {
		    //JFrame win = new SecondaryDiskMenu(frame);
		    SecondaryDiskMenu sdm = new SecondaryDiskMenu(frame,new File("c:\\"));  //TODO FIXME needs to be non-constant (should be outDirVal probably)
		    DiskData myDisk = sdm.showDialog();
		    if(myDisk.isValid())
		    {
			    postOutput("Added secondary data disk: \n" + myDisk + "\n");
			    ddList.add(myDisk);
		    }
		    else
		    {
			    postOutput("no disk added\n");
		    }


		    /*
		    System.err.println("HH" + s + "HH");
		    if(s.equals("") || s.equals("ImageFile: [Select Your Image File(s)]"))
		    {
			    postOutput("no disk added\n");
		    }
		    else
		    {
			    postOutput("Added secondary data disk: \n" + s + "\n");
		    }
		    */
		    //XXXX
	    }
	    });

	    advancedMenu.add(secondaryDiskMenuItem);

            menuBar.add(fileMenu);         //add the file menu to the menu bar
            menuBar.add(advancedMenu);
            menuBar.add(helpMenu);

            frame.setJMenuBar(menuBar);   //add the menu bar to the frame
            frame.pack();
            frame.setVisible(true);
        }

        /**
         *  Performs a variety of steps that need to be done to ensure that an image can be booted in vmware
         * 
         *   This operation must be performed to correct the known 0x7b blue screen error on Win2k and XP
         *   systems that were installed on a chipset that is incompatible with VMWare's Intel based 
         *   BX440 motherboard, A driver and registry changes to the critical device database in the 
         *  are made to permit booting, This is all done inside a vmware redo file so no changes are 
         *  actually made to the underlying disk image. 
         *  
         *  @param vmxLoc the path the vmware vmx file
         *  @param vmdkLoc the path to the vmware vmdk file
         *  @param mountDriveLetter the drive letter on the Windows OS intended for mounting the vmdk
         *  @param userChosenGuestOS the OS the use selected from the GUI (or auto)
         *  @param fsType the file system type of the partition
         *  @param partitionIndex the partition on the disk to prepare for booting
         *  @param clearPasswords true if passwords are desired to be blanked   #####Gov#####
         *  @param clearDomainPasswords true if cached domain passwords are desired to be blanked   #####Gov#####
         *  @param dumpHives true if the registry hives are desired to be dumped   #####Gov#####
         *  @param outputDir the path to create files in (such as a snapshot)
         *  @param baseFileName used for determining the filename of the snapshot file(s)
         *  @return the OperatingSystem instance for partition 'partitionIndex' in the image
         */
        private static OperatingSystem[] prepareVMForLaunch(   String vmxLoc, String vmdkLoc, String mountDriveLetter, 
                String userChosenGuestOS, String fsType, boolean isFullDisk, int partitionIndexIn,
                boolean clearPasswords, boolean clearDomainPasswords, boolean dumpHives,  /*#####Gov#####*/
                String outputDir, String baseFileName, JFrame frame)
        {
            boolean autoDetect = userChosenGuestOS.equals("auto");   //did user select auto detect os
System.out.println("incoming " + userChosenGuestOS);
	    int partitionIndex = partitionIndexIn;
	    myLogWriter.log("prepareVMForLaunch called with: \n\t" +
				vmxLoc + "\n\t" +
				vmdkLoc + "\n\t" +
				mountDriveLetter + "\n\t" +
				userChosenGuestOS + "\n\t" +
				fsType + "\n\t" +
				isFullDisk + "\n\t" +
				partitionIndex + "\n\t" +
				outputDir + "\n\t" +
				baseFileName + "\n\t" +
				"\n");
            if(isVMWare()){
		    if(isVMWareServer)
		    {
			if(addToVMServerConfigFile(vmxLoc))   //vmware server maintains a list of known virtual machines. We need to add the vmx to the list or it cannot be opened
			    postOutput("VMX added to VMWare Server Config" + endL);
			else
			{
			    postError("Failed to add vmx to VMWare Server Config File");
			    return null;
			    //return false;
			}
		    }
	     }

	    //create snapshot so we can redirect all disk access to it
	    if(createSnapshot(vmxLoc,baseFileName))
	    {
		postOutput("Snapshot Created" + endL);
	    }
	    else
	    {
		postError("Snapshot Creation Failed");
		return null;
		//return false;
	    }
	    
            String vmdkSnapshotLoc = vmdkLoc.substring(0,vmdkLoc.length()-5).concat("-000001.vmdk");   //xyz.vmdk -> xyz-000001.vmdk  (snapshot naming convention)

            String osProductName;

            //create os variable for the current image
            //OperatingSystem os = null;
	    OperatingSystem[] tempos = {null,null,null,null};

            try
            {
                if(autoDetect)
		    for(int part=1; part <= 4; part++){ 
			    tempos[part-1] = null;
			    //tempos = new OperatingSystem(mountDriveLetter, vmdkSnapshotLoc, partitionIndex, fsType, "auto");   //creat os and auto detect
                            postOutput("Autodetecting on partition " + part +"..........\n");
			    try{
			        tempos[part-1] = new OperatingSystem(mountDriveLetter, vmdkSnapshotLoc, part, fsType, "auto");   //creat os and auto detect
			    }
			    catch(OpenLVException lve){
				postError(lve.getMessage());
				tempos[part-1] = null;
			    }
				if(unmountSnapshot(mountDriveLetter, false))   //unmount the snapshot for image from local machine FS
				{
				    myLogWriter.log("Snapshot Unmounted" + endL);
				}
				else
				{
				    postError("Snapshot Unmount Failed");
					if(unmountSnapshot(mountDriveLetter, false))   //unmount the snapshot for image from local machine FS
					{
					    myLogWriter.log("Snapshot forcibly Unmounted" + endL);
					}else{
					    myLogWriter.log("Snapshot not Unmounted" + endL);
					    getNextFreeDriveLetter(mountDriveLetter.charAt(0));
					    myLogWriter.log("changing mountDriverLetter to " + mountDriveLetter);
					}
				}    
		    }
                else{
                    tempos[partitionIndex] = new OperatingSystem(mountDriveLetter, vmdkSnapshotLoc, partitionIndex, fsType, userChosenGuestOS);   //create os with user defined val
		}
            }
            catch(OpenLVException lve)
            {
                postError(lve.getMessage());
                cleanUp();
                return null;
                //return false;   
            }
	        
	    //heuristic for deciding which partition is the "important" one, on multi-boot partitions... TODO
	    //even though we can 'fixup' them all, only one type can be suggested in the VMX 
	    //TODO allow user to overide this via GUI
	    for( int part = 1; part <= 4; part++){
		    if(tempos[part-1] != null){
			    partitionIndex = part;
		    }
	    }
	    postOutput("Selected partition" + partitionIndex +":"+tempos[partitionIndex-1].getPublicOSName() + " as VM type" + endL);

	    //fixup ALL partitions that were recognized
	    if(isVMWare() && isWindows()){  //TODO fix for Virtualbox FIXME
		    for(int part=1; part <= 4; part++){ 
			OperatingSystem os = null;
			if(tempos[part-1] != null){
			    os = tempos[part-1];
			    postOutput("Analyzing partition" + part +" (" + os.getPublicOSName() + ") for error conditions..." + endL);
			    if(os != null){
				if(!mountSnapshot(mountDriveLetter, vmdkSnapshotLoc, part)){
				    postOutput("OS mount failed! exiting" + endL);
				    cleanUp();
				    return null;
				}else{
				    postOutput("partition mounted" + endL);
				}

			    }else{
				    postOutput("Valid OS not found! exiting" + endL);
				    cleanUp();
				    return null;
			    }

			    String guestOSVal = null;
			    if(autoDetect)
			    {
				osProductName = os.getPublicOSName();//queryRegistryForOSName(softHiveLoc);
				if(!osProductName.equalsIgnoreCase("Win9xOrLinux"))   //if not windows 9x or linux
				{
				    guestOSVal = os.getVmGuestOS();

				    if(OperatingSystem.getBaseOS(os.getVmGuestOS()).equals("nt"))
				    {
					//unmount the snapshot (because it was mounted earlier to detect NT and system dir)
					if(unmountSnapshot(mountDriveLetter, false))   //unmount the snapshot for image from local machine FS
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
				else   //win 9x or Linux -- TODO rather than default to "other" try to figure out actual guest os val
				{
				    guestOSVal = os.getVmGuestOS();//"other";
				    osProductName = os.getPublicOSName();//"Windows 9x or Linux";

				    //unmount the snapshot (because it was mounted earlier to detect win9x)
				    if(unmountSnapshot(mountDriveLetter, false))   //unmount the snapshot for image from local machine FS
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

				if(guestOSVal == null)   //no close guest OS match could be found for OS name
				{
				    postError("Unknown Operating System: " + osProductName + " Please manually choose the most similar OS from the dropdown and try again");
				    //return false;
				    return null;
				}      

				//add osname to vmx
				//since this file should only be written once, only perform action on 'primary' OS partition
				if(part == partitionIndex){
					DataOutputStream vmxOutStream = null;
					try
					{
					    vmxOutStream = new DataOutputStream(new FileOutputStream(vmxLoc,true));
					    vmxOutStream.writeBytes("guestOS=\"" + guestOSVal + "\"" + endL);      //append guestOS="<osname>" to vmx file
					    myLogWriter.log("Added: " + "guestOS=\"" + guestOSVal + "\"" + " to " + vmxLoc);
					    vmxOutStream.flush();
					    vmxOutStream.close();
					    postOutput("Added guest OS (" + guestOSVal + ") to vmx file for Parition (" +part+")" + endL);
					}
					catch(IOException ioe)
					{
					    postError("Error writing vmx file: " + vmxLoc);
					    //return false;
					    return null;
					}
				}
			    }
			    else   //not auto detect os
			    {
				guestOSVal = userChosenGuestOS;
				osProductName = os.getPublicOSName();
			    }

			    //if osname chosen is Me,98 or any other that doesnt require pre boot prep
			    if(!autoDetect && !OperatingSystem.isNTKernel(guestOSVal) && !guestOSVal.equalsIgnoreCase("linux"))   //if not xp, 2k, 2k3, or linux
			    {
				/*##########start-gov##########*/          
				if(dumpHives && autoDetect)   //user selected dump sam and system hives to output dir   and os is not nt based          
				{                        
				    postOutput("Skipping sam dump because autodetected OS does not support this feature" + endL);
				}
				if(clearDomainPasswords && autoDetect)   //user selected dump sam and system hives to output dir   and os is not nt based          
				{                        
				    postOutput("Skipping clearing of cached domain passwords because autodetected OS does not support this feature" + endL);
				}
				if(clearPasswords && autoDetect)   //user selected dump sam and system hives to output dir   and os is not nt based          
				{                        
				    postOutput("Skipping clearing of passwords because autodetected OS does not support this feature" + endL);
				}
				/*##########end-gov##########*/   

				//nt bluescreens when mounted so as a *WORKAROUND* we do not mount nt partitions anymore

				//          if(OperatingSystem.getBaseOS(userChosenGuestOS).compareTo("nt") == 0)
				//          {
				//             //original nt was previously mounted so unmount it here since no further processing is required
				//             if(unmountSnapshot(mountDriveLetter, false))   //unmount the snapshot for image from local machine FS
				//             {
				//                postOutput("Snapshot Unmounted" + endL);
				//             }
				//             else
				//             {
				//                postError("Snapshot Unmount Failed");
				//                //return false;
				//                return null;
				//             }    
				//          }

				//return os;//return osName; //we are done - no further prep needed for these os'
			    }
			    else  //it is NT or linux
			    {

			    //handle Windows NT separately
			    boolean isOriginalNT = false;
			    if(OperatingSystem.getBaseOS(userChosenGuestOS).compareTo("nt") == 0)
				isOriginalNT = true;

			    boolean isXP2Kor2K3 = OperatingSystem.isNTKernel(os.getVmGuestOS());   //doesnt include original NT

			    if(isXP2Kor2K3 /*|| isOriginalNT*/)   //if OS is NT kernel based or original NT (eg NT4.0)
			    {
				/*##########start-gov##########*/          
				//TODO Strange problem when clear passwords is checked for NT, it bluescreens on boot -- without it is fine (maybe mount/unmount happens too fast?)
				if(dumpHives)   //user selected dump sam and system hives to output dir             
				{                                                                
				    if(dumpRegHives(os, mountDriveLetter, outputDir, baseFileName))             
				    {                                                             
					postOutput("SAM, SYSTEM, and SECURITY Hives Successfully Extracted Into Output Directory" + endL);    
				    }                                                             
				    else                                                          
				    {                                                             
					postError("Error Extracting SAM, SYSTEM and SECURITY Hives From Image. Please Make Sure You Have Selected the Correct OS For Your Image From The Dropdown Menu");    
					//return false;
					return null;                                                 
				    }                                                             
				}                                                                

				if(enableAdvanced){
					if(clearDomainPasswords)   //user selected blank out all logon passwords                
					{                                                                
					    //modify DCC to clear passwords
					    String[] dusersCleared = null;
					    if((dusersCleared = clearDomainPasswords(os, mountDriveLetter, outputDir, baseFileName)) != null)                            
					    {                                                             
						postOutput("Passwords cleared for the following Domain users: " + endL);
						StringBuffer sb = new StringBuffer();
						for(int i = 0; i < dusersCleared.length - 1; i++)
						    sb.append(dusersCleared[i] + ", ");
						sb.append(dusersCleared[dusersCleared.length - 1]);
						postOutput(sb.toString() + endL);
					    }                                                             
					    else                                                          
					    {                                                             
						postError("Error Clearing Cached Domain Logon Passwords. Uncheck The Clear Passwords Box And Try Again.");    
						//return false;
						return null;                                                 
					    }                                                             
					}                                                                
				}
				if(clearPasswords)   //user selected blank out all logon passwords                
				{                                                                
				    //modify SAM file to clear passwords
				    String[] usersCleared = null;
				    if((usersCleared = clearLocalPasswords(os, mountDriveLetter, outputDir, baseFileName)) != null)                            
				    {                                                             
					postOutput("Passwords cleared for the following Local users: " + endL);
					StringBuffer sb = new StringBuffer();
					for(int i = 0; i < usersCleared.length - 1; i++)
					    sb.append(usersCleared[i] + ", ");
					sb.append(usersCleared[usersCleared.length - 1]);
					postOutput(sb.toString() + endL);
				    }                                                             
				    else                                                          
				    {                                                             
					postError("Error Clearing Logon Passwords. Uncheck The Clear Passwords Box And Try Again.");    
					//return false;
					return null;                                                 
				    }                                                             
				}                                                                
				/*##########end-gov##########*/       

				if(!isOriginalNT)
				{   
				    //try to extract the intelide.sys driver from one of the driver cache cab files on the image
				    if(extractDriver("intelide.sys", os, mountDriveLetter))
				    {
					postOutput("Intel IDE Driver Ready" + endL);
				    }
				    else   //extracting the driver from the image failed, try to find it on the host OS
				    {
					postOutput("Driver Extraction From Image Failed, Checking Local Filesystem" + endL);

					//copy intelide.sys driver to prevent 0x7b blue screen error on XP,2k,2003
					String driverFileLoc = InternalConfigStrings.getString("OpenLVLauncher.DriverFileLocation");

					if(copyDriver(driverFileLoc, os, mountDriveLetter))   
					{
					    postOutput("Intel IDE Driver Ready" + endL);
					}
					else
					{
					    postError("Adding Intel IDE Driver Failed.");
					    postError("Make Sure You Have Selected The Correct OS From The Choices Above");
					    unmountSnapshot(mountDriveLetter, true);         //force unmount of snapshot
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

				    if(loadSystemHive(systemHiveLoc))   //load the image system hive into local system registry
				    {
					postOutput("System Hive Loaded" + endL);
				    }
				    else
				    {
					postError("System Hive Load Failed");
					//return false;
					return null;
				    }

				    int currentControlSetVal = getCurrentControlSet();
				    if(currentControlSetVal == -1)   //failed to extract control set
					postError("Failed to extract CurrentControlSet value from guest registry");
				    else
					postOutput("Extracted Current Control Set Value: " + currentControlSetVal + endL);


				    //String macAddress = getMACAddress(currentControlSetVal);
				    //postOutput("MAC: " + macAddress);

				    //merge registry entries to loaded hive
				    String mergeTemplateLoc = InternalConfigStrings.getString("OpenLVLauncher.MergeFileLocation");

				    if(makeChangesToRegistry(mergeTemplateLoc, currentControlSetVal))   
				    {
					postOutput("Critical Device Database Updated" + endL);
				    }
				    else
				    {
					postError("Critical Device Database Update Failed");
					//return false;
					return null;
				    }
				}   //if not original NT

				if(isFullDisk)   //if we are dealing with a full disk
				{
				    if(!isOriginalNT)
				    {
					//unload system hive
					if(unloadHive("SYSTEM"))   //unload the image system hive from local machine' registry
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
				    if(unmountSnapshot(mountDriveLetter, false))   //unmount the snapshot for image from local machine FS
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
				else   //partition only, so keep mounted snapshot open -- other things need to be done for partitions later
				    postOutput("Keeping mounted snapshot open and registry loaded for partition" + endL);
			    }
			    else
			    {
				/*##########start-gov##########*/          
				if(dumpHives && autoDetect)   //user selected dump sam and system hives to output dir   and os is not nt based          
				{                        
				    postOutput("Skipping sam dump because autodetected OS does not support this feature" + endL);
				}
				if(clearDomainPasswords && autoDetect)   //user selected dump sam and system hives to output dir   and os is not nt based          
				{                        
				    postOutput("Skipping clearing of cached domain passwords because autodetected OS does not support this feature" + endL);
				}
				if(clearPasswords && autoDetect)   //user selected dump sam and system hives to output dir   and os is not nt based          
				{                        
				    postOutput("Skipping clearing of passwords because autodetected OS does not support this feature" + endL);
				}
				/*##########end-gov##########*/   
				//postOutput("OS is not NT Based" + endL);
			    }
			    }
			}
		    }    
	    }//TODO fix for virtual box
	    //heuristic for deciding which partition is the "important" one, on multi-boot partitions... TODO
	    /*
	    for( int part = 1; part <= 4; part++){
		    if(tempos[part-1] != null){
                            postOutput("autodetected OS on partition" + part +":"+tempos[part-1].getPublicOSName() + endL);
			    os =tempos[part-1];
			    partitionIndex = part;
		    }
	    }
	    postOutput("Selected partition" + partitionIndex +":"+tempos[partitionIndex-1].getPublicOSName() + endL);
	    */
            //return true;//return osName;
	    //postOutput("XXXXXXXXXXXXXXXXXXXXXXXXXXXXX\nreturning " + os.getPublicOSName());
            //return os;
	    return tempos;
        }

        /**
         * Creates a snapshot for a virtual machine to which we can direct all disk
         * modifications leaving the original image file untouched 
         * 
         * Snapshots are created differently depending on whether vmware server or workstation
         * is being used
         *
         * The "unique filename" requirement for snapshots on server is fullfilled by using current host system time
         *
         * @param vmxLoc the path to the vmware vmx file
         * @param imageName the vm image name 
         * @returns true for successful snapshot creation, false otherwise
         */
        private static boolean createSnapshot(String vmxLoc,String imageName)
        {
            String[] cmd = null;
	    if(isVMWare()){
		    if(!isVMWareServer)      //VMWare Workstation
		    {
			cmd = new String[4];
			cmd[0] = VMWARE_VMRUN_PATH;      //vmrun
			cmd[1] = "snapshot";   
			cmd[2] = vmxLoc;
			cmd[3] = "Original" + System.currentTimeMillis();      //unique snapshot name
		    }
		    else               //VMWare Server
		    {
			cmd = new String[3];
			cmd[0] = VMWARE_VMRUN_PATH;      //vmrun
			cmd[1] = "snapshot";   
			cmd[2] = vmxLoc;
		    }
		    String stdOut = callExternalProcess(cmd);
		    if(stdOut == null)
			return false;
		    return true;
	    }else if(isVirtualBox()){
		if(!snapshotVirtualBox(imageName)){
			postError("vbox snapshot error");
			return false;
		}	
		return true;
	    }else{
		postError("snapshot error, no virtualization type");
		System.exit(1);
	    }

            return false;
        }

        /*##########start-gov##########*/
        /**
         * Pulls the SAM, SYSTEM and Security hives out of the image and outputs them to the specified output 
         * directory for use in archical, cracking, etc
         * 
         * Precondition: Snapshot is already mounted 
         *
         * @param os an OperatingSytem instance to pull hives from
         * @param destDriveLetter this parameter is actually not used, but remains for historical reasons and uniformity
         * @param outputDir the path to save the hives to
         * @param baseFileName the base name (used as filenames for dumped files)
         * @return true on success, false otherwise
         */
        private static boolean dumpRegHives(OperatingSystem os, String destDriveLetter, String outputDir, String baseFileName)
        {
	    myLogWriter.log("dumpRegHives called with: \n\t" +
				os.getPublicOSName() + "\n\t" +
				destDriveLetter + "\n\t" +
				outputDir + "\n\t" +
				baseFileName + "\n\t" +
				"\n");

            String hiveDir;

            String systemRoot = os.getSystemRoot();
            if(systemRoot != null)
                hiveDir = systemRoot + "\\system32\\config\\";
            else
                return false;   //unhandled os

            myLogWriter.log("Hive Directory: " + hiveDir);

            FileChannel sourceChannel = null, destinationChannel = null;

            /* copy sam file to output dir */   
            File outSamFile = new File(outputDir + "\\" + baseFileName + ".SAM");
            File inSamFile = new File(hiveDir + "SAM");   

            try
            {
                sourceChannel = new FileInputStream(inSamFile).getChannel();         //source sam file
                destinationChannel = new FileOutputStream(outSamFile).getChannel();      //dest sam file
                sourceChannel.transferTo(0, sourceChannel.size(), destinationChannel);   //copy SAM file

                if(sourceChannel != null)
                    sourceChannel.close();
                if(destinationChannel != null)
                    destinationChannel.close();
            }
            catch(FileNotFoundException fnf)
            {
                postError("File Not Found -- Problem Extracting SAM Hive File: " + fnf.toString());
                return false;
            }
            catch(IOException ioe)
            {
                postError("I/O Problem Extracting SAM Hive File: " + ioe.toString());
                return false;
            }


            /* Copy SYSTEM hive file to output dir */
            File outSysFile = new File(outputDir + "\\" + baseFileName + ".SYSTEM");
            File inSysFile = new File(hiveDir + "SYSTEM");   

            try
            {
                sourceChannel = new FileInputStream(inSysFile).getChannel();
                destinationChannel = new FileOutputStream(outSysFile).getChannel();
                sourceChannel.transferTo(0, sourceChannel.size(), destinationChannel);   //copy SYSTEM file from source to dest

                if(sourceChannel != null)
                    sourceChannel.close();
                if(destinationChannel != null)
                    destinationChannel.close();
            }
            catch(FileNotFoundException fnf)
            {
                postError("File Not Found -- Problem Extracting SYSTEM Hive File: " + fnf.toString());
                return false;
            }
            catch(IOException ioe)
            {
                postError("I/O Problem Extracting SYSTEM Hive File: " + ioe.toString());
                return false;
            }

            /* Copy SECURITY hive file to output dir */
            File outSecFile = new File(outputDir + "\\" + baseFileName + ".SECURITY");
            File inSecFile = new File(hiveDir + "SECURITY");

            try
            {
                sourceChannel = new FileInputStream(inSecFile).getChannel();
                destinationChannel = new FileOutputStream(outSecFile).getChannel();
                sourceChannel.transferTo(0, sourceChannel.size(), destinationChannel);   //copy SECURITY file from source to dest

                if(sourceChannel != null)
                    sourceChannel.close();
                if(destinationChannel != null)
                    destinationChannel.close();
            }
            catch(FileNotFoundException fnf)
            {
                postError("File Not Found -- Problem Extracting SECURITY Hive File: " + fnf.toString());
                return false;
            }
            catch(IOException ioe)
            {
                postError("I/O Problem Extracting SECURITY Hive File: " + ioe.toString());
                return false;
            }

            return true;
        }
        /*##########end-gov##########*/


        /*##########start-gov##########*/
        /** 
         * Blanks out local SAM passwords on an image so that it can be booted directly
         * with blank passwords
         * 
         * Precondition: Snapshot is already mounted 
         * 
         * @param os and OperatingSystem instance
         * @param destDriveLetter this parameter is actually not used, but remains for historical reasons and uniformity
	 * @param outputDir output directory
	 * @param baseFileName the base file name for dumped hashes
         * @return a list of user accounts that had passwords cleared, null on failure
         */
        private static String[] clearLocalPasswords(OperatingSystem os, String destDriveLetter, String outputDir, String baseFileName)
        {
            //       String baseOS = getBaseOS(OS);
            String samLoc;
	    String sysLoc;

            String systemRoot = os.getSystemRoot();
            if(systemRoot != null){
                sysLoc = systemRoot + "\\system32\\config\\SYSTEM";
                samLoc = systemRoot + "\\system32\\config\\SAM";
	    }else
                return null;   //unhandled os selected

            myLogWriter.log("********************SYSTEM Location: " + sysLoc);
            myLogWriter.log("SAM Location: " + samLoc);

            return RegistryParser.clearLocalPasswords(sysLoc,samLoc,outputDir,baseFileName);
        }
        /*##########end-gov##########*/

        /*##########start-gov##########*/
        /** 
         * Blanks out Domain Cached Creds on an image so that it can be booted directly
         * with blank passwords
         * 
         * Precondition: Snapshot is already mounted 
         * 
         * @param os and OperatingSystem instance
         * @param destDriveLetter this parameter is actually not used, but remains for historical reasons and uniformity
	 * @param outputDir output directory
	 * @param baseFileName the base file name for dumped hashes
         * @return a list of user accounts that had passwords cleared, null on failure
         */
        private static String[] clearDomainPasswords(OperatingSystem os, String destDriveLetter, String outputDir, String baseFileName)
        {
            String sysLoc;
            String secLoc;

            String systemRoot = os.getSystemRoot();
            if(systemRoot != null){
                sysLoc = systemRoot + "\\system32\\config\\SYSTEM";
                secLoc = systemRoot + "\\system32\\config\\SECURITY";
	    }else
                return null;   //unhandled os selected

            myLogWriter.log("SYSTEM Location: " + sysLoc);
            myLogWriter.log("SECURITY Location: " + secLoc);

            return RegistryParser.clearDomainPasswords(sysLoc,secLoc,outputDir,baseFileName);
        }
        /*##########end-gov##########*/

        /**
         * Calls an external process and posts the stderr messages to the output window
         * 
         * @param cmd[] the command to execute 
         * @param show if true, the output buffer also is visible in the command window (optional)
         * @return the output buffer from the executed command, null on failure
         */
        public static String callExternalProcess(String[] cmd){
            return callExternalProcess(cmd, true);
        }

        public static String callExternalProcess(String[] cmd, boolean show)
        {
            myLogWriter.log("Executing: " + Arrays.toString(cmd));
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

                //check for exit val - eg any errors
                int exitVal = externalProc.waitFor();

                myLogWriter.log("External Proc returned: " + exitVal);

                myLogWriter.log("External Proc Output: " + stdOutReader.getReturnText());

                String errorMsg = stdErrReader.getReturnText();
                if((errorMsg.trim().length() > 0) && (show == true)){
                    postError("ep: " + errorMsg);
                }
                else {
                    myLogWriter.log("External Process Error: " + errorMsg);
                }

                if(exitVal == 0)   //no error
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

        /**
         * Mounts a vmware snapshot (*.vmdk) on the local disk so that it can be modified as if it
         * were another mounted filesystem on the local machine. 
         *
         * @param driveLetter the host drive letter to mount the vmdk to
         * @param snapshotVMDKLoc path to the vmware vmdk to mount
         * @param partition partiton to mount
         * @return true on successful mount, false otherwise
         */
        public static boolean mountSnapshot(String driveLetter, String snapshotVMDKLoc, int partition)
        {
	    myLogWriter.log("mountSnapshot called with: \n\t" +
				driveLetter + "\n\t" +
				snapshotVMDKLoc + "\n\t" +
				partition + "\n\t" +
				"\n");
	    String[] cmd = null;
	    if(isVMWare()){	
		if(isWindows()){
		    cmd = new String[4];
		    cmd[0] = VMWARE_MOUNT_PATH;//InternalConfigStrings.getString("OpenLVLauncher.VMMountExecutableLocation");      //vmware-mount
		    cmd[1] = "/v:" + partition;
		    cmd[2] = driveLetter + ":";   
		    cmd[3] = snapshotVMDKLoc;
		}
		else if(isLinux()){
		    //vmware-mount /path/to/disk-000001.vmdk 1 /path/to/mountpoint
		    cmd = new String[4];
		    cmd[0] = VMWARE_MOUNT_PATH;
		    cmd[1] = snapshotVMDKLoc;
		    cmd[2] = "" + partition;
		    cmd[3] = driveLetter;   
		    
		}
		else{
		   return false;
		}
	    }

            String stdOut = callExternalProcess(cmd);
            if(stdOut == null)
                return false;
            return true;

        }

        /**
         * Loads the system hive (of the image) in the local system's registry under the branch:
         * HKLM\NEWSYSTEM
         * This allows us to query/modify the system hive of the image as if it was part of the local
         * system's registry (as opposed to writing an offline registry hive editor)
         *
         * @param systemHiveLoc the dumped hive file for import into the registry
         * @return true on successful load, false otherwise
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

        /**
         * Loads the hardware hive (of the image) in the local system's registry under the branch:
         * HKLM\NEWHARDWARE
         * This allows us to query/modify the system hive of the image as if it was part of the local
         * system's registry (as opposed to writing an offline registry hive editor)
         *
         * @param hardwareHiveLoc the dumped hive file for import into the registry
         * @return true on successful load, false otherwise
         */
        public static boolean loadHardwareHive(String hardwareHiveLoc)
        {
            //check if the hardware hive actually exists
            File hardwareHiveFile = new File (hardwareHiveLoc);
            if(!hardwareHiveFile.exists())
            {
                postError("Hardware hive file could not be found on disk image"); 
                return false;
            }

            String[] cmd = new String[4];
            cmd[0] = "reg";
            cmd[1] = "load";   
            cmd[2] = "HKLM\\NEWHARDWARE";
            cmd[3] = hardwareHiveLoc;

            String stdOut = callExternalProcess(cmd);
            if(stdOut == null)
                return false;
            return true;
        }

        /**
         * Loads the software hive (of the image) in the local system's registry under the branch:
         * HKLM\NEWSOFTWARE
         * This allows us to query/modify the system hive of the image as if it was part of the local
         * system's registry (as opposed to writing an offline registry hive editor)
         *
         * @param softwareHiveLoc the dumped hive file for import into the registry
         * @return true on successful load, false otherwise
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

        /**
         * Applies the contents of the merge file (*.reg) to the registry to make appropriate changes
         * to the critical device database so that the intelide.sys driver is loaded on boot to prevent
         * the 0x7b bluescreen boot errors. 
         * 
         * Precondition: system hive from image is already loaded into local system registry under HKLM\NEWSYSTEM
         *
         * @param mergeTemplatLoc the path to the .reg file for import into the mounted hive
         * @param currentControlSetVal the control sent in use by the OS
         * @return true on success, false otherwise
         */
        private static boolean makeChangesToRegistry(String mergeTemplateLoc, int currentControlSetVal)
        {      
            File tempMergeFile = null;

            //read in merge template line by line
            StringBuffer mergeTemplateBuffer = new StringBuffer();
            try
            {
                DataInputStream in = new DataInputStream(new FileInputStream(mergeTemplateLoc));
                BufferedReader br = new BufferedReader(new InputStreamReader(in));
                String currLine = null;
                while((currLine = br.readLine()) != null)   
                    mergeTemplateBuffer.append(currLine + endL);
                in.close();

                //pad the control set val to make it 3 chars long
                String controlSetString = "ControlSet"  + new Formatter().format("%03d", currentControlSetVal).toString();

                //replace the control set placeholder with the actual current control set
                String finalMergeFileString = mergeTemplateBuffer.toString().replaceAll("<CurrentControlSet>", controlSetString);

                //write modified merge file contents to temp merge file
                tempMergeFile = new File(mergeTemplateLoc + ".temp");
                FileWriter outWriter = new FileWriter(tempMergeFile);
                outWriter.write(finalMergeFileString);
                outWriter.close();
            }
            catch(IOException ioe)
            {
                postError("I/O Error while creating merge file from template. Guest registry could not be updated" + endL + ioe.getMessage());
                return false;         
            }

            //run temporary merge file
            String[] cmd = new String[3];
            cmd[0] = "regedit";
            cmd[1] = "/s";   
            cmd[2] = tempMergeFile.getAbsolutePath();

            String stdOut = callExternalProcess(cmd);
            tempMergeFile.delete();  //delete temporary merge file
            if(stdOut == null)
                return false;
            return true;
        }

        /**
         * Extracts the specified driver from the specified .cab to the appropriate drivers directory 
         * based on the OS passed in, The intelide.sys driver is necessary for overcoming the 0x7B blue 
         * screen error that occurs when booting a system on intel hardware (emulated by vmware) when it 
         * was not originally installed on different hardware. 
         *
         * @param driverNameToExtract the driver to extract, for use in vmware
         * @param os the OperatingSystem instance
         * @param destDriveLetter this parameter is actually not used, but remains for historical reasons and uniformity
         * @return true on success, false otherwise
         */
        private static boolean extractDriver(String driverNameToExtract, OperatingSystem os, String destDriveLetter)
        {

            String driverDestinationLoc = null;
            String systemRootDir = os.getSystemRoot();
            if(systemRootDir != null)
                driverDestinationLoc = os.getSystemRoot() + "\\system32\\drivers";
            else 
                return false;    //unhandled os selected

            myLogWriter.log("Driver Destination Location: " + driverDestinationLoc);

            //test if the intelide.sys driver is already present
            File f = new File(driverDestinationLoc + "\\" + driverNameToExtract);
            if(f.exists())
            {
                postOutput("Intel IDE Driver Already Exists On The System, Skipping Extraction" + endL);
                return true;
            }
            else
                myLogWriter.log("intel ide driver not found in driver directory on image");

            String driverCabLoc = null;
            String cabPrefix = systemRootDir + "\\Driver Cache\\i386";

            File cabFile = null;
            boolean foundCab = false;
            for(int i = 0; i < 10 && !foundCab; i++)   //check spX.cab where X is the service pack number
            {
                driverCabLoc = cabPrefix + "\\sp" + i + ".cab";
                cabFile = new File(driverCabLoc);
                if(cabFile.exists())
                {
                    foundCab = true;
                    myLogWriter.log("Found: " + cabFile.getName());
                }
            }
            if(!foundCab)      //no spX.cab files found, check driver.cab
            {
                driverCabLoc = cabPrefix + "\\driver.cab";
                myLogWriter.log("No spX.cab found, looking for: " + driverCabLoc);
                cabFile = new File(driverCabLoc);
                if(cabFile.exists())
                {
                    myLogWriter.log("Found: " + driverCabLoc);
                    foundCab = true;
                }
            }

            if(!foundCab)   //no spX.cab or driver.cab files found (error)
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

        /**
         * Copies the specified driver to the appropriate drivers directory based on the OS passed in
         * The intelide.sys driver is necessary for overcoming the 0x7B blue screen error that occurs 
         * when booting a system on intel hardware (emulated by vmware) when it was not originally installed
         * on different hardware. ]
         *
         * @param driverSourceLoc the path to the extracted driver
         * @param os the OperatingSystem instance
         * @param destDriveLetter this parameter is actually not used, but remains for historical reasons and uniformity
         * @return true on success, false otherwise
         */
        private static boolean copyDriver(String driverSourceLoc, OperatingSystem os, String destDriveLetter)
        {
            String driverDestinationLoc = null;

            //       String baseOS = getBaseOS(OS);

            String systemRootDir = os.getSystemRoot();
            if(systemRootDir != null)
                driverDestinationLoc = os.getSystemRoot() + "\\system32\\drivers";
            else 
                return false;    //unhandled os selected

            myLogWriter.log("Driver Destination Location: " + driverDestinationLoc);

            try    //copy intelde.sys driver to drivers directory so it can be booted in vmware's intel based vm
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

        /**
         * Unloads the system hive of the image from the local system's registry 
         * 
         * Precondition:
         * This assumes that the image's system/software hive was previously loaded under HKLM\NEWSYSTEM
         * or HKLM\NEWSOFTWARE
         * 
         * @param hiveType the type of hive (SYSTEM or SOFTWARE)
         * @return true on success, false otherwise
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

        /** 
         * VMWare server maintains a list of vmx files it knows about (presumably as some sort of sanity check), This
         * method adds the vmx being launched to that list so that vmware server will permit a snapshot to be created
         *
         * @param vmxPath the path to the vmx file
         * @return true on success, false on failure
         */
        private static boolean addToVMServerConfigFile(String vmxPath)
        {
            BufferedWriter bw = null;

            String commonAppDataPath = queryRegistry("HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders", "Common AppData", "REG_SZ");
            String pathToVMList = commonAppDataPath + InternalConfigStrings.getString("OpenLVLauncher.VMWareServerVMListLocation");//System.getenv("APPDATA") + InternalConfigStrings.getString("OpenLVLauncher.VMWareServerVMListLocation");

            String stringToAdd = "config " +  "\"" + vmxPath + "\"";

            myLogWriter.log("Path TO VMServer vm-list: " + pathToVMList);
            myLogWriter.log("String to add: " + stringToAdd);

            //check if the entry is already there
            try 
            {
                FileInputStream fin =  new FileInputStream(pathToVMList);
                BufferedReader bufReader = new BufferedReader(new InputStreamReader(fin));

                String line;
                while ((line = bufReader.readLine()) != null) 
                {  
                    if(line.equals(stringToAdd))
                    {
                        myLogWriter.log("vmx was already in vm-list, skipping append");
                        return true;            //vmx is already in the config file
                    }
                }
            }
            catch (Exception e) 
            {
                postError("Could not read the VMWare Server vm-list file");
                return false;   //error reading file
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
            { //close the file
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

        /**
         * Opens the specified page in the defualt browser Used for launching the
         * help doc from the help menu item
         *
         * @param url a URL to open in the default browser
         * @return true on success, false otherwise
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

        /**
         * Unmounts a snapshot from local disk
         * 
         * @param driveLetter the drive letter associated with the mounted snapshot
         * @param force if true, use the forcable unmount feature of vmware-mount
         * @return true on success, false otherwise
         */
        private static boolean unmountSnapshot(String driveLetter, boolean force)
        {
            String[] cmd = new String[3];
	    if(isWindows()){
		    cmd[0] = VMWARE_MOUNT_PATH;//InternalConfigStrings.getString("OpenLVLauncher.VMMountExecutableLocation"); //vmware-mount
		    cmd[1] = driveLetter + ":";
	    }
	    else if(isWindows()){
		    cmd[0] = VMWARE_MOUNT_PATH;//InternalConfigStrings.getString("OpenLVLauncher.VMMountExecutableLocation"); //vmware-mount
		    cmd[1] = driveLetter + ":";
	    }
	    else{
		return false;
	    }

            if(force)
		if(isWindows())	
			cmd[2] = "/f";   //forcibly unmount
		else
			cmd[2] = "-f";   //forcibly unmount
            else
		if(isWindows())
			cmd[2] = "/d";   //gracefully unmount
		else
			cmd[2] = "-d";   //gracefully unmount

            String stdOut = callExternalProcess(cmd);
            if(stdOut == null)
                return false;
            return true;
        }

        /**
         * Loads up vmware and starts the virtual machine associated with the vmx 
         * This method is only currently used for starting a VM with VMWare Workstation (not server) 
         *
         * @param progName the vmrun executable
         * @param command vmrun command (start)
         * @param vmxFilePath path to vmware vmx file
         * @return true on success, false otherwise
         */
        private static boolean startVMProc(String progName, String command, String vmxFilePath)
        {

            final String[] cmd = new String[3];   

            cmd[0]  = progName;
            cmd[1]  = command;
            cmd[2]   = vmxFilePath;

            postOutput("Attempting to Launch Forensic Image in Virtual Machine" + endL);
            postOutput("Please Wait..." + endL);

            String stdOut = callExternalProcess(cmd);
            if(stdOut == null)
                return false;
            return true;
        }

        /**
         *  Starts the vmware server console in preparation for launching the image
         *  Note: I used rundll32 here because vmware.exe directly did not seem to return
         *  an exit value until the console is closed.  
         *
         *  @param vmxPath the path to the vmware vmx file
         *  @return true on success, false otherwise
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

        /**
         * create a VirtualBox vm 
         * 
         * @param imageName the displayname for the vm
         * @param osType of the evidence files (for the vm)
         * @param basePath location to output the vm (the vm will automatically appear in virtualbox gui)
         * @return true iff success
         */
        private static boolean createVirtualBox(String imageName, String osType, String basePath)
        {
            if(isVMWare()){
		    //error
		    postError("vbox add settings but isVMWare..");
		    return false;
	    }else if(isVirtualBox()){
 		File test = new File(basePath);
		if(!test.exists()){
			postError("directory doesn't exist at: " + basePath);
			return false;
		}
		if(!test.canWrite()){
			postError("directory isn't writable at: " + basePath);
			return false;
		}
		//VBoxManage -q createvm --name imageName --ostype Windows --register --basefolder /some/path
		    String[] cmd = new String[9];
		    cmd[0] = VIRTUAL_BOX_PATH;         
		    cmd[1] = "createvm"; 
		    cmd[2] = "--name";
		    cmd[3] = imageName;    //name of VM
		    cmd[4] = "--ostype";   
		    cmd[5] = osType;   		
		    cmd[6] = "--register";   
		    cmd[7] = "--basefolder";   
		    cmd[8] = basePath;   

		    String stdOut = callExternalProcess(cmd);
		    if(stdOut == null)
			return false;
		    return true;

		    }
	    return false;
        }
        /**
         * creates a VirtualBox vm snapshop
         * 
         * @param imageName the displayname for the vm
         * @return true iff success
         */
        private static boolean snapshotVirtualBox(String imageName)
        {
            if(isVMWare()){
		    //error
		    postError("vbox snapshot error but isVMWare..");
		    return false;
	    }else if(isVirtualBox()){
		//VBoxManage snapshot imageName take snap1 --description lvsnap
		    String[] cmd = new String[7];
		    cmd[0] = VIRTUAL_BOX_PATH;         
		    cmd[1] = "snapshot";
		    cmd[2] = imageName;    //name of VM
		    cmd[3] = "take";   
		    cmd[4] = "snap1";   		
		    cmd[5] = "--description";   
		    cmd[6] = "lvsnap";   

		    String stdOut = callExternalProcess(cmd);
		    if(stdOut == null)
			return false;
		    return true;

		    }
	    return false;
        }
        /**
         * starts a VirtualBox vm 
         * 
         * @param imageName the displayname for the vm
         * @return true iff success
         */
        private static boolean startVirtualBox(String imageName )
        {
            if(isVMWare()){
		    //error
		    postError("vbox start but isVMWare..");
		    return false;
	    }else if(isVirtualBox()){
		//VBoxManage -q modifyvm imageName --memory MEMORY --ioapic on
		    String[] cmd = new String[5];
		    cmd[0] = VIRTUAL_BOX_PATH;         
		    cmd[1] = "startvm";
		    cmd[2] = imageName;    //name of VM
		    cmd[3] = "--type";   
		    cmd[4] = "gui";   		

		    String stdOut = callExternalProcess(cmd);
		    if(stdOut == null)
			return false;
		    return true;

		    }
	    return false;
        }
        /**
         * adds settings to a VirtualBox vm 
         * 
         * @param imageName the displayname for the vm
         * @param mem the ammount of memory for the vm
         * @return true iff success
         */
        private static boolean addSettingsToVirtualBox(String imageName, String mem)
        {
            if(isVMWare()){
		    //error
		    postError("vbox add settings but isVMWare..");
		    return false;
	    }else if(isVirtualBox()){
		//VBoxManage -q modifyvm imageName --memory MEMORY --ioapic on
		    String[] cmd = new String[7];
		    cmd[0] = VIRTUAL_BOX_PATH;         
		    cmd[1] = "modifyvm";
		    cmd[2] = imageName;    //name of VM
		    cmd[3] = "--memory";   
		    cmd[4] = mem;   		
		    cmd[5] = "--ioapic";   
		    cmd[6] = "on";   

		    String stdOut = callExternalProcess(cmd);
		    if(stdOut == null)
			return false;
		    return true;

		    }
	    return false;
        }
        /**
         * adds a storage controller to a VirtualBox vm 
         * 
         * @param imageName the displayname for the vm
         * @return true iff success
         */
        private static boolean addControllerToVirtualBox(String imageName)
        {
            if(isVMWare()){
		    //error
		    postError("vbox add controller but isVMWare..");
		    return false;
	    }else if(isVirtualBox()){
		//VBoxManage -q storagectl imageName --name ide1 --add ide --bootable on --controller PIIX3
		    String[] cmd = new String[11];
		    cmd[0] = VIRTUAL_BOX_PATH;         
		    cmd[1] = "storagectl";
		    cmd[2] = imageName;    //name of VM
		    cmd[3] = "--name";   
		    cmd[4] = "ide1";   		//must match name in storageattach
		    cmd[5] = "--add";   
		    cmd[6] = "ide";   
		    cmd[7] = "--bootable";   
		    cmd[8] = "on";   
		    cmd[9] = "--controller";   
		    cmd[10] = "PIIX3";   	//seems to work well, might need to inject an appropriate driver TODO

		    String stdOut = callExternalProcess(cmd);
		    if(stdOut == null)
			return false;
		    return true;

		    }
	    return false;
        }
        /**
         * adds a vmdk vmware disk specification as an available disk to VirtualBox 
         * 
         * @param pathToVMDKFile path to the vmware vmx file
         * @param imageName the displayname for the vm
         * @return true iff success
         */
        private static boolean addVmdkToVirtualBox(String pathToVMDKFile,String imageName)
        {
            if(isVMWare()){
		    //error
		    postError("vbox add disk but isVMWare..");
		    return false;
	    }else if(isVirtualBox()){
 		//VBoxManage -q storageattach --name imageName --type hdd --medium <vmdkfile> --storagectl IDE
 		File test = new File(pathToVMDKFile);
		if(!test.exists()){
			postError("can't find vmdk at" + pathToVMDKFile);
			return false;
		}
		    String[] cmd = new String[13];
		    cmd[0] = VIRTUAL_BOX_PATH;         
		    cmd[1] = "storageattach";
		    cmd[2] = imageName;    //name of VM
		    cmd[3] = "--type";   
		    cmd[4] = "hdd";   
		    cmd[5] = "--medium";   
		    cmd[6] = pathToVMDKFile;   
		    cmd[7] = "--storagectl";   
		    cmd[8] = "ide1";   		//must match name from storagectl command
		    cmd[9] = "--port";   
		    cmd[10] = "0";   
		    cmd[11] = "--device";   
		    cmd[12] = "0";   

		    String stdOut = callExternalProcess(cmd);
		    if(stdOut == null)
			return false;
		    return true;

		    }
	    return false;
        }

        /**
         * Utility function that determines whether a snapshot is associted with the vm
         * 
         * @param pathToVMXFile path to the vmware vmx file
         * @param imageName the displayname for the vm
         * @return true = a snapshot is associated with the vm, false = no snapshot associated with vm
         */
        private static boolean snapshotExists(String pathToVMXFile,String imageName)
        {
            if(isVMWare()){
		    String snapshotPath = pathToVMXFile.substring(0,pathToVMXFile.length()-4).concat("-000001.vmdk");   //standard naming convention for snapshots
		    File testFile = new File(snapshotPath);   
		    return testFile.exists();   //does a file by that name exist
	    }else if(isVirtualBox()){
 		//Virtualbox has a command
		//VBoxManage snapshot thenamehere list
//HERE
		    String[] cmd = new String[4];
		    cmd[0] = VIRTUAL_BOX_PATH;         
		    cmd[1] = "snapshot";
		    cmd[2] = imageName;    //name of VM
		    cmd[3] = "list";   

		    String stdOut = callExternalProcess(cmd);
		    if(stdOut == null)
			return false;
		    return true;

		    }
	    return false;
        }

        /**
         * Utility function that returns the number of snapshots associated with 
         * a particular vm
         * 
         * Currently Unused -- because listSnapshots does not appear to work properly with VMware Server
         *
         * @param pathToVMXFile path the vmware vmx file
         * @return 1  = 1 snapshot, 0  = no snapshots, -1 = error detecting snapshots
         */
        private static int numSnapshots(String pathToVMXFile)
        {
            String[] cmd = new String[3];
            cmd[0] = VMWARE_VMRUN_PATH;      //vmrun
            cmd[1] = "listSnapshots";   
            cmd[2] = pathToVMXFile;

            int numSnapshots = -1;

            File vmx = new File(pathToVMXFile);
            if(!vmx.exists())         //if vmx file does not even exist, numSnapshots is zero
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

        /** 
         * Read in the generic MBR, Modify the end CHS address and size 
         * bytes and write out to modifiedMBR 
         *
         * @param sizeOfPartition size of partition (sectors) to write to the MBR partition table
         * @param numHeads populates the number of heads portion of the partition table
         * @param genericMBR java File instance to a generic MBR to modify
         * @param modifiedMBR java File instance for the newly created MBR
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
                for(int i = 0; i < genericMBR.length(); i++)   //0-511 bytes + 62 blank sectors
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
                            c += 64;         //set bit 7 of sector byte (corresponds to bit 9 of cylinder)
                        if((tmp & 0x200) > 0) 
                            c += 128;          //set bit 8 of sector byte (corresponds to bit 10 of cylinder)
                    }
                    else if(i == 453)
                        c = (int)((sizeOfPartitionSectors / numHeads / SECTORS_PER_TRACK) & 0xFF);
                    else if(i == 458)   // modify bytes 458-461 of generic MBR with size of partition
                        c = (int)sizeOfPartitionSectors & 0xFF;                     //byte 12 of bootable partition entry
                    else if(i == 459)
                        c = (int)((sizeOfPartitionSectors & 0xFF00) >> 8);            //byte 13 of bootable partition entry
                    else if(i == 460)
                        c = (int)((sizeOfPartitionSectors & 0xFF0000) >> 16);         //byte 14 of bootable parititon entry
                    else if(i == 461)
                        c = (int)((sizeOfPartitionSectors & 0xFF000000) >> 24);         //byte 15 of bootable partition entry

                    modifiedMBROut.write(c);   //write the byte to the modified mbr
                }

                genericMBRIn.close();
                modifiedMBROut.close();
            }
            catch (IOException ioe)
            {
                postError("There was an I/O problem writing an MBR for your partition " + ioe.getMessage());
            }
        }

        /**
         * determines the current control set in use in the registry
         *
         * Precondition: Registry is already loaded and will be unloaded after method returns 
         * return null means failure querying registry
         *
         * @return the current control set
         */
        private static int getCurrentControlSet()
        {
            //search registry key HKLM\NEWSYSTEM\Select\Current to find the current control set number
            String regData = queryRegistry("HKLM\\NEWSYSTEM\\Select",
                    "Current",
                    "REG_DWORD");

            if(regData != null)
            {
                try
                {
                    return  Integer.parseInt(regData.substring(2,regData.length()), 16);    //chop off hex value are convert to int;
                }
                catch(NumberFormatException nfe)
                {
                    postError("CurrentControlSet is not an integer");
                    return -1;
                }
            } 
            return -1;         //some sort of failure to extract value
        }

        /**
         * Extracts the disk serial number from the registry
         * 
         * This is necessary for building the mbr for partitions because the disk serial number
         * is used by some operating systems to maintain the drive to letter mappings (eg the bootable
         * is 'c:') Without the correct serial number in the mbr, the OS may not be able to find that mapping
         * in the registry and may hang when trying to boot. 
         *
         * @param vmdkLoc the path to the vmware vmdk
         * @param useExistingSnapshot true if you with to use an existing snapshot
         * @param os the OperatingSystem instance
         * @param ntPartitionIndex which partition within the disk to use
         * @returns an integer array of the serial number, null on error
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

                myLogWriter.log("System Hive Loc: " + systemHiveLoc);

                if(serialNum != null && systemHiveLoc != null)   //we know it is either an XP or 2K aliased OS
                    worked = loadSystemHive(systemHiveLoc);

                if(!worked)
                {
                    postError("System Hive Load For Serial Number Failed");
                    serialNum = null;
                }
                else
                    postOutput("Loaded System Hive For Disk Serial Number: " + endL);
            }

            //get current control set value to pass to boot drive letter
            int currentControlSetVal = getCurrentControlSet();
            if(currentControlSetVal == -1)   //failed to extract control set
                postError("Failed to extract CurrentControlSet value from guest registry necessary for extracting boot drive letter");
            //       else
            //          postOutput("Extracted Current Control Set Value: " + currentControlSetVal + endL);

            //get registry key and parse out boot drive letter (mounted snapshot and registry should be open and loaded already from prepareForLaunch())
            bootDriveLetter = getBootDriveLetter(currentControlSetVal);   

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
                    diskSerialString = regData.substring(0,8);    //chop off first 8 characters (4 hex bytes)
                    myLogWriter.log("Disk Serial Number: " + diskSerialString);

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

        /**
         * determine the drive letter for the bootable parition
         *
         * Precondition: Registry is already loaded and will be unloaded after method returns 
         * return null means failure querying registry
         *
         * @param controlSetVal the control set in use in the registry
         * @return the drive letter, as a string, null on error
         */
        private static String getBootDriveLetter(int controlSetVal)
        {
            //pad control set value in string with 3 chars
            String controlSetString = "ControlSet"  + new Formatter().format("%03d", controlSetVal).toString();

            //search registry key HKLM\NEWSYSTEM\<CurrentControlSet>\Control\ContentIndex\DllsToRegister to find bootable drive letter
            String regData = queryRegistry("HKLM\\NEWSYSTEM\\" + controlSetString + "\\Control\\ContentIndex",
                    "DllsToRegister",
                    "REG_MULTI_SZ");

            if(regData != null)
                return  regData.substring(0,1);    //chop off the first character (drive letter);

            return null;         //unknown string found
        }

        /**
         * determine guests original MAC address
         *
         * Precondition: Registry is already loaded and will be unloaded after method returns 
         * return null means failure querying registry
         *
         * @param controlSetVal the control set in use in the registry
         * @return the MAC address, as a string, null on error
         */
        private static String getMACAddress(int controlSetVal)
        {
            //pad control set value in string with 3 chars
            String controlSetString = "ControlSet"  + new Formatter().format("%03d", controlSetVal).toString();

	    //win2k/xp: HKEY_LOCAL_MACHINE\SYSTEM\CurrentControlSet\ Control\Class\{4D36E972-E325-11CE-BFC1-08002BE10318}
	    //          must search recursively because it will be uner \0001, 0002, 0003...etc as NetworkAddress
	    //win9x:    HKEY_LOCAL_MACHINE\System\ CurrentControlSet\Services\Class\Net
	    //secondary: HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows Genuine Advantage
            String regData = queryRegistry("HKLM\\NEWSYSTEM\\" + controlSetString + "\\Control\\Class\\{4D36E972-E325-11CE-BFC1-08002BE10318}",
                    "NetworkAddress",
                    "REG_SZ");

            if(regData != null)
                return  regData;

            return null;         //unknown string found
        }

        /**
         * determine the ammount of installed RAM 
         *
         * Precondition: Registry is already loaded and will be unloaded after method returns 
         * return null means failure querying registry
         *
         * @return installed memory ammount in bytes, -1 on error
         */
        private static long getInstalledMemory()
        {
            //pad control set value in string with 3 chars

	    if(isWindows()){
		    //search registry key HKLM/HARDWARE/RESOURCEMAP/System Resources/Physical Memory for RAM amount
		    String regData = queryRegistry2("HKLM\\HARDWARE\\RESOURCEMAP\\System Resources\\Physical Memory",
			    ".Translated",
			    "REG_RESOURCE_LIST");

		    String count, iface, bus, ver, rev, unk, unk2, resType, disp, access, toss, base, length;
		    count = iface = bus = ver = rev = unk = unk2 = resType = disp = access = toss = base = length = null;
		    long ramTotal = 0;
		    if(regData != null){
			    //postOutput(" \n\n memory: \n" + regData + "\n");
			    //regData = removeSpaces(regData);
			    //regData = getOnlyNumeric(regData);
			    int idx=0;
			    count = regData.substring(idx,idx+8);
			    //postOutput("" + count + "\n");
			    idx +=8;
			    iface = regData.substring(idx,idx+8);
			    //postOutput("" + iface + "\n");
			    idx +=8;
			    bus = regData.substring(idx,idx+8);
			    //postOutput("" + bus + "\n");
			    idx +=8;
			    ver = regData.substring(idx,idx+4);
			    //postOutput("" + ver  + "\n");
			    idx +=4;
			    unk = regData.substring(idx,idx+4);
			    //postOutput("" + unk + "\n");
			    idx +=4;
			    unk2 = regData.substring(idx,idx+8);
			    //postOutput("" + unk2 + "\n");
			    idx +=8;
			    do{
				    resType = regData.substring(idx,idx+2);
				    //postOutput("" + resType + "\n");
				    idx +=2;
				    disp = regData.substring(idx,idx+2);
				    //postOutput("" + disp + "\n");
				    idx +=2;
				    access = regData.substring(idx,idx+2);
				    //postOutput("" + access + "\n");
				    idx +=2;
				    toss = regData.substring(idx,idx+2);
				    //postOutput("" + toss + "\n");
				    idx +=2;
				    base = regData.substring(idx,idx+16);
				    //postOutput("" + base + "\n");
				    
				    
				    idx +=16;
				    length = regData.substring(idx,idx+16);
				    //postOutput("" + length + "\n" );
				    long num = Long.parseLong(length,16);
				    //postOutput("This is long:=" + num + "\n");
				    long res = bigToLittleEndian(num);  
				    //postOutput("ram segment found: " +Long.toHexString(res) + " " + res/1024/1024 + "\n");
				    ramTotal += res;
				    idx +=16;
			    }
			    while(idx< regData.length());
	 
			    //postOutput("total ram: " + Long.toHexString(ramTotal) + " " + ramTotal/1024/1024 + "\n");
		    }
		    return ramTotal;
		}
		    else if(isLinux()){
			// cat /proc/meminfo | grep MemTotal
			File memFile = new File("/proc/meminfo");
			if(memFile.exists()){
			    String[] cmd = new String[2];
			    cmd[0] = "cat";
			    cmd[1] = "/proc/meminfo";

			    String ret = "";

			    String outputBuffer = callExternalProcess(cmd,true);  //TODO false

			    if(outputBuffer == null)
				return -1;

			    String[] outputLines = outputBuffer.split(System.getProperty("line.separator"));

			    String line;
			    boolean done = false;
			    int offset;

			    for(int i = 0; !done && i < outputLines.length; i++)   //for every output line
			    {
				line = outputLines[i];
				if(line.trim().startsWith("MemTotal"))
				{
				    //MemTotal: 32234234 kb
				    //TODO FIXME should use regex
				    ret = line.substring(9,(line.length()-3));
				    done = true;
				}
			    }
			    //TODO FIXME
			    //return Integer.parseInt(ret);
			    return 0;

			}

		    }


            return -1;         //unknown string found
        }
	private static long bigToLittleEndian(long bigendian) {  
	    ByteBuffer buf = ByteBuffer.allocate(8);  
	   
	    buf.order(ByteOrder.BIG_ENDIAN);  
	    buf.putLong(bigendian);  
	   
	    buf.order(ByteOrder.LITTLE_ENDIAN);  
	    return buf.getLong(0);  
	}  

	private static String removeSpaces(String s) {
	  StringTokenizer st = new StringTokenizer(s," ",false);
	  String t="";
	  while (st.hasMoreElements()) t += st.nextElement();
	  return t;
	}
	private static String getOnlyNumeric(String str) {
	    
	    if (str == null) {
		return null;
	    }

	    StringBuffer strBuff = new StringBuffer();
	    char c;
	    
	    for (int i = 0; i < str.length() ; i++) {
		c = str.charAt(i);
		
		if (Character.isDigit(c)) {
		    strBuff.append(c);
		}
	    }
	    return strBuff.toString();
	}
	
	
        /**
         * Unmounts mounted image and unloads the registry (for instance when program is closed prematurely)
         */   
        private static void cleanUp()
        {
            if(startWasClicked && externalProc != null)
            {
                postOutput("Cleaning up..." + endL);
                String mountDriveLetter = MOUNT_DRIVE_LETTER; //ExternalConfigStrings.getString("Configuration.DriveMountLetter");
                unmountSnapshot(mountDriveLetter, true);   //force unmount of disk image
                unloadHive("SYSTEM");
                unloadHive("SOFTWARE");
            }
        }

        /**
         * Creates a random point point
         * 
         * @param prefix a path where the random directory should be created 
         * @return the mountpoint
         */
        private static String getMountPoint(String prefix)
        {

	    //TODO FIXME

	    //chould create the directory
	    //if the directory already exists, create a different one
	    
            return "/tmp/asdf";   
        }
        /**
         * Finds next open drive letter on system starting at the startVal and desc
         * 
         * @param startVal the drive letter to start with
         * @return an available drive letter
         */
        private static String getNextFreeDriveLetter(char startVal)
        {
            char candidateDriveLetter = startVal;
            File[] usedDriveLetters = File.listRoots();   

            String    currDriveLetterInUse; 

            while(candidateDriveLetter <= 'z')   //for all candidate drive letters
            {
                //check if current candidate matches any drive letters in use
                boolean isCandidateInUse = false;
                for(int i = 0; i < usedDriveLetters.length; i++) //for all used drive letters
                {
                    currDriveLetterInUse = usedDriveLetters[i].getPath().substring(0,1);
                    if(Character.toString(candidateDriveLetter).equalsIgnoreCase(currDriveLetterInUse))
                    {
                        isCandidateInUse = true;
                        break;   //candidate matches a drive letter in use, so go to next candidate
                    }
                }
                if(!isCandidateInUse) //if candidate is free, return it
                    return Character.toString(candidateDriveLetter);
                candidateDriveLetter++;
            }
            return null;   //error, all drive letters are being used
        }

        /**
         * Sorts the file extensions of chunked images so that they can be 
         * put in order before attempting to boot the image
         * 
         * Sorts purely numeric extensions numerically, and purely alphabetic and alphanumeric
         * extensions alphabetically
         *
         * @param chunkFiles the image file chunk array
         */
        private static void sortChunkFileNamesByExtension(String[] chunkFiles)
        {
            String[] extensions = new String[chunkFiles.length];      //string file extensions for chunkFiles
            int[]    numericExtensions = new int[chunkFiles.length];   //numeric file extensions
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
                        extValDec = Integer.parseInt(extensions[i]);   //check if extension is an integer
                        numericExtensions[i] = extValDec;
                        isNumber = true; 
                    }
                    catch(NumberFormatException nfe)
                    {
                        if(isNumber == true)
                        {
                            inconsistent = true;
                            myLogWriter.log("Found Inconsistent Image File Extension: " + extensions[i]);
                        }
                    }         
                }
            }

            if(isNumber && !inconsistent)   //if we have all numeric extensions
            {
                myLogWriter.log("All numeric extensions");
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
                myLogWriter.log("Sorted extensions numerically");
                //         System.out.println("Sorted Numerically: " + Arrays.toString(chunkFiles));
            }
            else   //not numeric extensions (or mixed extensions)
            {
                myLogWriter.log("non-numeric or mixed extensions detected");
                Arrays.sort(chunkFiles);   //sort alphabetically
                //         System.out.println("sorted alphabetically: " + Arrays.toString(chunkFiles));
            }
        }
        /**
         * Checks if the current machine has vmware server or vmware workstation installed
         * Depricated 12/16/08, use getVMWareVersion() instead
         *
         * @return  1 = vmware server, 0 = vmware workstation, -1 = no vmware installation detected
         */
        private static int isUsingVMWareServer()
        {
            String vmType = queryRegistryForVMWareCore();//ExternalConfigStrings.getString("Configuration.VMWareVersion");

            if(vmType == null)
                return -1;

            //      System.err.println("TMV: ********************" + vmType);

            if(vmType.compareToIgnoreCase("VMware Server Standalone") == 0)
                return 1;
            else
                return 0;   
        }


        private static final String getVMWareRegPath()
        {
            String vmRegPath = "HKLM\\SOFTWARE\\VMware, Inc.";

            //search registry key HKLM\SOFTWARE\VMware, Inc.\Core
            String regData = queryRegistry(vmRegPath,
                    "Core",
                    "REG_SZ");

            if (regData != null) {
                myLogWriter.log(vmRegPath);
                return vmRegPath;
            }

            vmRegPath = "HKLM\\SOFTWARE\\WOW6432Node\\VMware, Inc.";

            //search registry key HKLM\SOFTWARE\WOW6432Node\VMware, Inc.\Core
            regData = queryRegistry(vmRegPath,
                    "Core",
                    "REG_SZ");

            if (regData != null) {
                myLogWriter.log(vmRegPath);
                return vmRegPath;
            }

            return null;
	} 


        /**
         * Collects ostypes from the installed version of virtualbox 
         *
         * @return  1 = some version of virtualbox, -1 = no virtualbox installation detected
         */
        private static int getVirtualBoxOSTypes(){
		int ret = -1;
		if(isLinux()){
			ret = getVirtualBoxOSTypesLinux();
		}
		else if(isWindows()){
			ret = getVirtualBoxOSTypesWin();
		}
		return ret;
        }
        /**
         * Collects ostypes from the installed version of virtualbox (windows)
         *
         * @return   1 some version of virtualbox is installed, -1 = no virtualbox installation detected
         */
        private static int getVirtualBoxOSTypesWin(){
		return -1;
	}
        /**
         * Collects ostypes from the installed version of virtualbox (linux)
         *
         * @return   1 some version of virtualbox is installed, -1 = no virtualbox installation detected
         */
        private static int getVirtualBoxOSTypesLinux(){
		int ret = -1;

            String[] cmd = new String[3];
            cmd[0] = VIRTUAL_BOX_PATH;
            cmd[1] = "list";
            cmd[2] = "ostypes";


            String outputBuffer = callExternalProcess(cmd,false);

            if(outputBuffer == null)
                return -1;

            String[] outputLines = outputBuffer.split(System.getProperty("line.separator"));

            String line;
            boolean done = false;
            int offset;

		java.util.List<String> iGuestOSVals = new ArrayList<String>();
		iGuestOSVals.add( "auto" );
		java.util.List<String> iOSVals = new ArrayList<String>();
		iOSVals.add( "Automatic" );


            for(int i = 0; i < outputLines.length; i++)   //for every output line
            {
                line = outputLines[i];
                if(line.trim().startsWith("ID:"))
                {
			iGuestOSVals.add(line.substring(3).trim());
			ret = 1;
                }
                if(line.trim().startsWith("Description:"))
                {
			iOSVals.add(line.substring(12).trim());
			ret = 1;
                }
            }
		
		guestOSVals = new String[ iGuestOSVals.size() ];
		iGuestOSVals.toArray( guestOSVals);

		osVals = new String[ iOSVals.size() ];
		iOSVals.toArray( osVals);


		return ret;

	}
        /**
         * Determines version of VirtualBox installed
         *
         * @return  1 = some version of virtualbox, -1 = no virtualbox installation detected
         */
        private static int getVirtualBoxVersion(){
		int ret = -1;
		if(isLinux()){
			ret = getVirtualBoxVersionLinux();
		}
		else if(isWindows()){
			ret = getVirtualBoxVersionWin();
		}
		return ret;
        }
        /**
         * Determines version of VirtualBox installed (linux specific)
         *
         * @return   1 some version of virtualbox is installed, -1 = no virtualbox installation detected
         */
        private static int getVirtualBoxVersionLinux(){
		int ret = -1;
                String vb = findBinary("VBoxManage");
                if(vb != null){
                   VIRTUAL_BOX_PATH = vb;
                   ret = 1;

                }

		return ret;

	}
        /**
         * Determines version of VirtualBox installed (windows specific)
         *
         * @return  2 = vmware server 2.x, 1 = vmware server 1.x, 0 = vmware workstation, -1 = no vmware installation detected
         */
        private static int getVirtualBoxVersionWin(){
		//TODO
		return -1;
	}
        /**
         * Determines version of VMWare installed
         *
         * @return  2 = vmware server 2.x, 1 = vmware server 1.x, 0 = vmware workstation, -1 = no vmware installation detected
         */
        private static int getVMWareVersion(){
		int ret = -1;
		if(isLinux()){
			ret = getVMWareVersionLinux();
		}
		else if(isWindows()){
			ret = getVMWareVersionWin();
		}
		return ret;
        }
        /**
         * Determines version of VMWare installed (linux specific)
         *
         * @return  2 = vmware server 2.x, 1 = vmware server 1.x, 0 = vmware workstation, -1 = no vmware installation detected
         */
        private static int getVMWareVersionLinux(){
	    //TODO implement
	    String vmbin = findBinary("vmware");
//HERE
	    //use vmware binary to determin version
            String[] cmd = new String[2];
            cmd[0] = vmbin;      //vmware
            cmd[1] = "--version";   


            String stdOut = callExternalProcess(cmd);

            if(stdOut == null)
		return -1;
            else
            {
	        thisComputerVMWare = stdOut.trim();
		if(stdOut.indexOf("Workstation") != -1){
			return 0;
		}
		//TODO server?
            }

	    return -1;

	}
        /**
         * Determines version of VMWare installed (windows specific)
         *
         * @return  2 = vmware server 2.x, 1 = vmware server 1.x, 0 = vmware workstation, -1 = no vmware installation detected
         */
        private static int getVMWareVersionWin()
        {
            String vmType = null;
            vmType = queryRegistryForVMWareCore();//ExternalConfigStrings.getString("Configuration.VMWareVersion");

            if(vmType == null)
                return verError;

	    thisComputerVMWare = vmType;

            //      System.err.println("TMV: ********************" + vmType);
            
            //if server
            //  if version == 2
            //     if vix

            if(vmType.compareToIgnoreCase("VMware Server Standalone") == 0)
                return verServerOne;
            else if(vmType.compareToIgnoreCase("VMware Server") == 0){
                System.err.println("\n" + queryRegistryForVMWareServerVersion());
                return verServerTwo;
            }
            else if(vmType.compareToIgnoreCase("VMware Workstation") == 0  ){
                String workstationVersion = queryRegistryForVMWareWorkstationVersion();  //likely only works for 6.0 +
                if(workstationVersion != null){  //key exists
                    System.err.println("\nwsv: " + workstationVersion + "\n");
		    thisComputerVMWare = thisComputerVMWare + " " + workstationVersion;
                    try{
                        if(Float.valueOf(workstationVersion.trim()).floatValue() < 5.5){
                            return verError;
                        } 
                    }
                    //catch (NumberFormatException nfe){
                    catch (Exception nfe){
                        System.err.println("\n" + queryRegistryForVMWareWorkstationVersion());
                        return verError;
                    }
                    return verWorkstation;   
                } else {
                    return verWorkstationOld;
                }
            }
            return verError;
        }

        /**
         *  Check HKLM\SOFTWARE\VMWare, Inc.\Core for versions of server or workstation
         *  
         *  Workstation and Server cannot be installed simultaneously (although
         *  registry entries from both may be present) so this key determines
         *  whether Worstation or Server is the "active" installation
         *  
         *  @return the current VMWare Core
         */
        private static String queryRegistryForVMWareCore()
        {
            //search registry key HKLM\SOFTWARE\VMWare, Inc.\Core
            String regData = queryRegistry(VMWARE_REG_PATH,
                    "Core",
                    "REG_SZ");

            if(regData == null)
                return regData;
            else if(regData.equalsIgnoreCase("VMware Server Standalone") ||
                    regData.equalsIgnoreCase("VMware Workstation") ||
                    regData.equalsIgnoreCase("VMware Server")
                   )
                return regData;      //return the core string

            return null;         //unknown string found

        }

        /**
         *  Looks for Version key of installed VMWare Workstation 
         *  @return the workstation version, null on error
         */
        private static String queryRegistryForVMWareWorkstationVersion()
        {
            //search registry key HKLM\SOFTWARE\VMWare, Inc.\Core
            //this key is only present on more recent versions of Workstation.  Many verions of 5.5 do not create this key.
            String regData = queryRegistry(VMWARE_REG_PATH + "\\VMware Workstation",
                    "ProductVersion",
                    "REG_SZ");

            //only return the "Major" version  (eg 6.5 instead of 6.5.8)
            if(regData != null){
                String tokens[] = regData.split("\\.");  // . is the regex metacharacter so escape it
                if(tokens.length >= 2){
                    return tokens[0] + "." + tokens[1];
                }
                else {
                    return regData;
                }

            }

            return null;         //unknown string found

        }
        /**
         *  Looks for Version key of installed server2 
         *  TODO this should be extended to work on server1 and workstation products
         *
         *  @return the version of server2
         */
        private static String queryRegistryForVMWareServerVersion()
        {
            //search registry key HKLM\SOFTWARE\VMWare, Inc.\Core
            String regData = queryRegistry(VMWARE_REG_PATH + "\\VMware Server\\License.gsx.3.0-00",
                    "ProductID",
                    "REG_SZ");

            if(regData == null)
                return regData;
            else if(regData.equalsIgnoreCase("VMware GSX Server for Win32") ||
                    regData.equalsIgnoreCase("VMware Workstation") ||
                    regData.equalsIgnoreCase("VMware Server")
                   )
                return regData;      //return the core string

            return null;         //unknown string found

        }

        /**
         * Query WMI Interface for physical devices attached to machine
         * Also checks each physical device on the system to catch devices not reported in WMI (eg w/ MIP)
         * 
         * @return array of combo box items representing the display string and underlying physical device address value pairs, null on failure 
         */
        public static PhysicalDiskInfo[] getPhysicalDeviceItems()
        {
            ArrayList diskInfoList = new ArrayList();
            PhysicalDiskInfo[] returnVal = null;

            Map validDeviceIndexMapping = getIndexInterfaceDeviceMapping();
            Map indexModelMapping = getIndexModelNameDeviceMapping(validDeviceIndexMapping);
            int hostBootDriveIndex = getHostBootDriveIndex();

            //get the index and size of each physical device attached to machine
            //wmic /namespace:\\root\cimv2 path Win32_DiskDrive get index, size
            String[] cmd = new String[7];
            cmd[0] = "wmic";
            cmd[1] = "/namespace" + ":\\\\root\\cimv2";   
            cmd[2] = "path";
            cmd[3] = "Win32_DiskDrive";
            cmd[4] = "get";
            cmd[5] = "index";
            cmd[6] = ",size";      

            String outputBuffer = callExternalProcess(cmd);

            if(outputBuffer == null)
                return null;

            String[] outputLines = outputBuffer.split(System.getProperty("line.separator"));

            String deviceIndex;
            double diskSize;

            String line;
            //parse index/size mapping
            for(int i = 0; i < outputLines.length; i++)   //for every output line
            {
                line = outputLines[i].trim();
                if(!line.startsWith("Index") && !line.equals(""))   //if line is not a column header 
                {   
                    deviceIndex = line.substring(0,1);   //extract index of this device
                    myLogWriter.log("Device Index: " + deviceIndex);

                    String sizeString = line.substring(1,line.length()).trim();
                    myLogWriter.log("Device Size: " + sizeString);

                    if(sizeString.length() > 0)   //check if a size is reported (size is not reported for devices like card readers when no card is inserted)
                    {
                        diskSize = Double.parseDouble(sizeString);   //extract size of disk for this index

                        if(diskSize > 0)   //sometimes size is reported as 0 for card readers and the like -- exclude these
                        {
                            if(indexModelMapping.containsKey(new Integer(deviceIndex)) && validDeviceIndexMapping.containsKey(new Integer(deviceIndex)))   //if current device is one in the indexmodel mapping (it is valid)
                            {
                                String model = (String)indexModelMapping.get(new Integer(deviceIndex));
                                String interfaceType = (String)validDeviceIndexMapping.get(new Integer(deviceIndex));
                                PhysicalDiskInfo pdi = new PhysicalDiskInfo(deviceIndex, interfaceType, model, diskSize);

                                if(!pdi.getIndex().equals(Integer.toString(hostBootDriveIndex)))   //skip host boot drive
                                {

                                    String wmiPhysicalDriveString = "\\\\.\\PhysicalDrive" + deviceIndex;
                                    RandomAccessFile wmiPhysicalDriveHandle;

                                    int[] unsignedTempMBRBuffer = null;
                                    try
                                    {
                                        wmiPhysicalDriveHandle = new RandomAccessFile(wmiPhysicalDriveString, "r");

                                        //read physical drive handle to get mbr and calculate total disk size
                                        byte[] tempMbrBuffer = new byte[512];   //signed mbr bytes
                                        wmiPhysicalDriveHandle.readFully(tempMbrBuffer);   
                                        wmiPhysicalDriveHandle.close();
                                        unsignedTempMBRBuffer = new int[512];   //unsigned bytes from mbr
                                        for(int x = 0; x < 512; x++)   //convert signed bytes to unsigned
                                        {
                                            unsignedTempMBRBuffer[x] = tempMbrBuffer[x];
                                            if(tempMbrBuffer[x] < 0)
                                                unsignedTempMBRBuffer[x] = 256 + tempMbrBuffer[x];
                                        }
                                    }
                                    catch(FileNotFoundException fnf)              
                                    {
                                        logError("Could not open physical device: " + wmiPhysicalDriveString + " " + fnf.getMessage());
                                        continue;
                                    }
                                    catch(IOException ioe)
                                    {
                                        logError("I/O problem reading physical device: " + wmiPhysicalDriveString + " " + ioe.getMessage());
                                        continue;
                                    }

                                    //if physical drive has a valid mbr, add it to the list of devices
                                    MasterBootRecord tmpMbr = new MasterBootRecord(unsignedTempMBRBuffer);
                                    if(tmpMbr.isValidMBR())
                                    {
                                        myLogWriter.log("Added " + wmiPhysicalDriveString + " detected via WMI with valid MBR to list of devices" + endL);
                                        diskInfoList.add(pdi);   //add it to list of disks
                                    }
                                    else
                                        myLogWriter.log("Skipped " + wmiPhysicalDriveString + " detected with WMI because it has an invalid MBR");
                                }
                                else
                                    myLogWriter.log("Excluded Device Index: " + deviceIndex + " because it is the host boot drive");
                            }
                            else
                                myLogWriter.log("WARNING: Current Device " + deviceIndex + " is not in indexModelMapping");
                        }
                        else
                            myLogWriter.log("Skipped Device: " + deviceIndex + " detected w/ WMI because a size of 0 was reported");
                    }
                    else
                        myLogWriter.log("Skipped Device: " + deviceIndex + " detected w/ WMI because no size was reported");
                }
            }

            //now iterate through all physical devices to find ones not reported by wmi because
            //of not relating logical to physical device (eg MIP)
            //final int MAX_PHYS_DRIVE_NUM = 40;  //FIXME
	    final int MAX_PHYS_DRIVE_NUM = Integer.parseInt(MAX_PHYS_DRIVE_NUM_STR);
            String physicalDriveString;
            for(int i = 0; i < MAX_PHYS_DRIVE_NUM; i++)   
            {
                if(i == hostBootDriveIndex)   //skip physical drive if it is the host os drive
                    continue;

                physicalDriveString = "\\\\.\\PhysicalDrive" + i;
                RandomAccessFile physicalDriveHandle;
                try
                {
                    physicalDriveHandle = new RandomAccessFile(physicalDriveString, "r");
                    physicalDriveHandle.getFD();   //try to get file descriptor (this will fail if phys drive doesnt exist)
                }
                catch (FileNotFoundException fnf)   
                {   
                    logError("Could not open physical device:  " + physicalDriveString + " " + fnf.getMessage());
                    continue;
                }
                catch (IOException ioe)
                {
                    logError("I/O problem reading physical device:  " + physicalDriveString + " " + ioe.getMessage());
                    continue;
                }           

                //found an actual physical device, check if we already found it with WMI 
                if(!indexModelMapping.containsKey(new Integer(i)))   //if we havent already found it with WMI
                {
                    PhysicalDiskInfo pdi;
                    int[] unsignedTempMBRBuffer = null;
                    try
                    {
                        //read physical drive handle to get mbr and calculate total disk size
                        byte[] tempMbrBuffer = new byte[512];   //signed mbr bytes
                        physicalDriveHandle.readFully(tempMbrBuffer);   
                        physicalDriveHandle.close();
                        unsignedTempMBRBuffer = new int[512];   //unsigned bytes from mbr
                        for(int x = 0; x < 512; x++)   //convert signed bytes to unsigned
                        {
                            unsignedTempMBRBuffer[x] = tempMbrBuffer[x];
                            if(tempMbrBuffer[x] < 0)
                                unsignedTempMBRBuffer[x] = 256 + tempMbrBuffer[x];
                        }
                    }
                    catch(FileNotFoundException fnf)              
                    {
                        logError("Could not open physical device: " + physicalDriveString + " " + fnf.getMessage());
                        continue;
                    }
                    catch(IOException ioe)
                    {
                        logError("I/O problem reading physical device: " + physicalDriveString + " " + ioe.getMessage());
                        continue;
                    }

                    //if physical drive has a valid mbr, add it to the list of devices
                    MasterBootRecord tmpMbr = new MasterBootRecord(unsignedTempMBRBuffer);
                    if(tmpMbr.isValidMBR())
                    {
                        double physicalDriveSizeBytes = tmpMbr.totalSectorsFromPartitions() * 512;
                        myLogWriter.log("Added " + physicalDriveString + " not detected by WMI to list of physical devices" + endL);
                        pdi = new PhysicalDiskInfo(Integer.toString(i), "IDE", "Hard Disk " + i, physicalDriveSizeBytes);
                        diskInfoList.add(pdi);   //add it to list of disks
                    }
                    else
                        myLogWriter.log("Skipped " + physicalDriveString + " because it has an invalid MBR");
                }
            }

            //convert arraylist into array of PhysicalDiskInfo structures
            returnVal = new PhysicalDiskInfo[diskInfoList.size()];
            for(int i = 0; i < diskInfoList.size(); i++)
            {
                returnVal[i] = (PhysicalDiskInfo)diskInfoList.get(i);
                myLogWriter.log("Physical Disk Info " + i + ": " + returnVal[i]);
            }

            return returnVal;
        }

        /**
         * Query WMI Interface for drive index of host's boot disk 
         * 
         * @return drive index for host OS, -1 indicates the query failed
         */
        private static int getHostBootDriveIndex()
        {
            //wmic /namespace:\\root\cimv2 path Win32_LogicalDisk where DeviceID="<boot_drive_letter>:" assoc /RESULTCLASS:WIN32_DiskPartition
            String[] cmd = new String[8];
            cmd[0] = "wmic";
            cmd[1] = "/namespace" + ":\\\\root\\cimv2";   
            cmd[2] = "path";
            cmd[3] = "Win32_LogicalDisk";
            cmd[4] = "where";
            cmd[5] = "DeviceID=" + '"' + System.getenv("SystemDrive") + '"';
            cmd[6] = "assoc";
            cmd[7] = "/RESULTCLASS:Win32_DiskPartition";

            String outputBuffer = callExternalProcess(cmd);

            if(outputBuffer == null)
                return -1;

            String[] outputLines = outputBuffer.split(System.getProperty("line.separator"));

            int deviceIndex = -1;

            String line;
            for(int i = 0; i < outputLines.length; i++)   //for every output line
            {
                line = outputLines[i].trim();

                String prefix = "Win32_DiskPartition.DeviceID=" + '"' + "Disk #";
                int beginningOfIndex = line.indexOf(prefix);   //beginning of prefix in line of output
                if(beginningOfIndex > 0)   //found prefix
                {
                    deviceIndex = Integer.parseInt(line.substring(beginningOfIndex + prefix.length(), beginningOfIndex + prefix.length() + 1));   //extract index value
                    myLogWriter.log("Bootable device is index: " + deviceIndex);           
                }
            }
            return deviceIndex;   
        }


        /**
         * Query WMI Interface for physical devices attached to machine that are USB or Firewire Interface
         * 
         * @return mapping of all USB and firewire device indices mapped to their interface type (usb or firewire), null on failure
         * 
         */
        private static Map getIndexInterfaceDeviceMapping()
        {
            Map deviceIndexMap = new HashMap();   //mapping of device index to interface

            //wmic /namespace:\\root\cimv2 path Win32_DiskDrive get index, InterfaceType
            String[] cmd = new String[7];
            cmd[0] = "wmic";
            cmd[1] = "/namespace" + ":\\\\root\\cimv2";   
            cmd[2] = "path";
            cmd[3] = "Win32_DiskDrive";
            cmd[4] = "get";
            cmd[5] = "index";
            cmd[6] = ",InterfaceType";      

            String outputBuffer = callExternalProcess(cmd);

            if(outputBuffer == null)
                return null;

            String[] outputLines = outputBuffer.split(System.getProperty("line.separator"));

            int deviceIndex;
            String interfaceType;

            String line;
            for(int i = 0; i < outputLines.length; i++)   //for every output line
            {
                line = outputLines[i].trim();
                if(!line.startsWith("Index") && !line.equals(""))   //if line is not a column header line
                {   
                    deviceIndex = Integer.parseInt(line.substring(0,1));   //extract index of this device
                    interfaceType = line.substring(1,line.length()).trim();   //extract interface type for this index

                    //if we found a usb or firewire interface 
                    //              if(interfaceType.equalsIgnoreCase("USB") || interfaceType.equalsIgnoreCase("1394"))
                    deviceIndexMap.put(new Integer(deviceIndex),interfaceType);   //add the device index to the list 
                }
            }

            return deviceIndexMap;
        }

        /**
         * Create a mapping between the valid physical device indices and their model names
         * 
         * @return a map of physical device index numbers to model names, null on failure
         */
        private static Map getIndexModelNameDeviceMapping(Map validDeviceIndexMapping)
        {
            Map indexToModelNameMap = new HashMap();

            //wmic /namespace:\\root\cimv2 path Win32_DiskDrive get index, model
            String[] cmd = new String[7];
            cmd[0] = "wmic";
            cmd[1] = "/namespace" + ":\\\\root\\cimv2";   
            cmd[2] = "path";
            cmd[3] = "Win32_DiskDrive";
            cmd[4] = "get";
            cmd[5] = "index";
            cmd[6] = ",model";      

            String outputBuffer = callExternalProcess(cmd);

            if(outputBuffer == null)
                return null;

            String[] outputLines = outputBuffer.split(System.getProperty("line.separator"));

            int deviceIndex;
            String modelName;

            String line;
            for(int i = 0; i < outputLines.length; i++)   //for every output line
            {
                line = outputLines[i].trim();
                if(!line.startsWith("Index") && !line.equals(""))   //if line is not a column header line
                {   
                    deviceIndex = Integer.parseInt(line.substring(0,1));   //extract index of this device
                    modelName = line.substring(1,line.length()).trim();      //extract model name for this index

                    if(modelName.length() <= 0)   //if no model name is reported by WMI, make a generic one (eg with PDE)
                        modelName = "Hard Disk " + deviceIndex;

                    //only add to map if current device index is a valid one
                    Set validDeviceIndices = validDeviceIndexMapping.keySet();
                    Iterator indexIterator = validDeviceIndices.iterator();
                    while(indexIterator.hasNext())
                    {
                        int currValidIndex = ((Integer)indexIterator.next()).intValue();
                        if(deviceIndex == currValidIndex)   //device index is valid
                            indexToModelNameMap.put(new Integer(deviceIndex), modelName);
                    }

                }
            }

            return indexToModelNameMap;
        }

        /**
         * Shells out to execute 'which' in order to find binary locations in linux
         * 
         * @param regPath the registry path to navigate to
         * @param regKeyName the key at regPath to inspect
         * @param regType the type of reg Value (such as REG_SZ)
         * @return the value (always as a string), null if the key doesn't exist
         */
        public static String findBinary(String bin)
        {
		
            String[] cmd = new String[2];
            cmd[0] = "which";
            cmd[1] = bin;

            String ret = null;

            String outputBuffer = callExternalProcess(cmd,true);  //TODO false

            if(outputBuffer == null)
                return null;

            String[] outputLines = outputBuffer.split(System.getProperty("line.separator"));

            String line;
            boolean done = false;
            int offset;

            for(int i = 0; !done && i < outputLines.length; i++)   //for every output line
            {
                line = outputLines[i];
                if(line.trim().endsWith(bin))
                {
                    ret = line.trim();
                    done = true;
                }
            }

            return ret;
        }
        /**
         * Searches the registry for a particular key and returns its associated value
         * 
         * example: regPath:    "HKLM\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion"
         *          regKeyName: "ProductName"
         *          regType:   "REG_SZ"
         *
         * @param regPath the registry path to navigate to
         * @param regKeyName the key at regPath to inspect
         * @param regType the type of reg Value (such as REG_SZ)
         * @return the value (always as a string), null if the key doesn't exist
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

            String outputBuffer = callExternalProcess(cmd,false);

            if(outputBuffer == null)
                return null;

            String[] outputLines = outputBuffer.split(System.getProperty("line.separator"));

            String line;
            boolean done = false;
            int offset;

            for(int i = 0; !done && i < outputLines.length; i++)   //for every output line
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
        public static String queryRegistry2(String regPath, String regKeyName, String regType)
        {
            //search registry for regPath\regKeyName
            String[] cmd = new String[3];
            cmd[0] = "reg";
            cmd[1] = "query";   
            cmd[2] = "\"" + regPath + "\"";
            //cmd[3] = "/v";
            //cmd[4] = regKeyName;

            String regData = null;

            String outputBuffer = callExternalProcess(cmd,false);

            if(outputBuffer == null)
                return null;

            String[] outputLines = outputBuffer.split(System.getProperty("line.separator"));

            String line;
            boolean done = false;
            int offset;

            for(int i = 0; !done && i < outputLines.length; i++)   //for every output line
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

        /**
         * Build the path to the vmrun executable (which is different based
         * on whether using VMWare server or Workstation. 
         *
         * @param isVMWareServer true if it is VMWare server 1.x
         * @return the path to vmrun
         */
        private static String getVMWareVMRunPath(boolean isVMWareServer)
        {
 	    if(isWindows()){
		    String installDir = getVMWareInstallDir(isVMWareServer);
		    if(isVMWareServer)
			return installDir + "..\\VMware VIX\\vmrun.exe";
		    else
			return installDir + "vmrun.exe";
	    }
            else if(isLinux()){
		return findBinary("vmrun");
	    }
	    return null;
        }


        /**
         * Check HKLM\SOFTWARE\VMWare, Inc.\VMware {Server|Workstation}\InstallPath for versions of VMWare
         *
         * @param isVMWareServer true if it is VMWare server 1.x
         * @return the install directory for the vmware product
         */
        private static String getVMWareInstallDir(boolean isVMWareServer)
        {
            StringBuffer regPath = new StringBuffer(VMWARE_REG_PATH);
            if(isVMWareServer)
                regPath.append("\\VMware Server");
            else
                regPath.append("\\VMware Workstation");

            return queryRegistry(regPath.toString(), "InstallPath", "REG_SZ");

        }

        /**
         * looks for VMWare Diskmount Utility installed with Server in order the user about incompatibily issues
         *
         * Note:  apparently SSLEAY32.dll is required for vmrun in server 1.0.8 and is only installed with Servers DiskMount Utility
         *        so we actually NEED this to be installed, and just make sure we use the VDDK version
         *
         * @return the path servers version of vmware-mount
         */

        // TMV TODO FIX server 1.0.8 location find
        private static String queryRegistryForServerVMMountPath()
        {
            String path = queryRegistry(VMWARE_REG_PATH + "\\Server\\vmware-mount.exe", "Path", "REG_SZ");
            if(path != null){
                myLogWriter.log("Server version of vmware-mount detected");
                return path + "vmware-mount.exe";
            }
            return path;
        }

        /**
         * looks for VMWare Diskmount Utility in order the user about incompatibily issues
         *
         * Check HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\vmware-mount.exe\Path 
         * for the path to the vmware-mount.exe executable
         *
         * @return the path to the old vmware-mount utility
         */
        private static String queryRegistryForOldVMMountPath()
        {
            String path = queryRegistry("HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\vmware-mount.exe", "Path", "REG_SZ");
            if(path != null){
                myLogWriter.log("Old workstation version of vmware-mount detected");
                return path + "vmware-mount.exe";
            }
            return path;
        }

        /**
         * checks for presence of the VMware VDDK for it's version of vmware-mount, 
         *
         * Check HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\vmware-mount.exe\Path 
         * for the path to the vmware-mount.exe executable
         *
         * @return the path to the vddk version vmware-mount
         */
        private static String queryRegistryForVDDKVMMountPath()
        {
            String path = queryRegistry(VMWARE_REG_PATH + "\\VMware Virtual Disk Development Kit", "InstallPath", "REG_SZ");
            if(path != null)
                return path + "bin\\vmware-mount.exe";
            return path;
        }


        /**
         * Kills the external process represented by global proc
         */
        private static void stopProc()
        {
            if(worker != null)
                worker.interrupt();   //interrupt running process
            if(externalProc != null)
                externalProc.destroy();      //kill the running process
        }

        /**
         * Posts an error message to the GUI output area and writes it to the log
         *
         * @param line a error string
         */
        public static void postError(String line)
        {
            myLogWriter.log("Error: " + line);
            messageOutputArea.append("ERROR>     " + line + endL);   //write the error message to out window
            messageOutputArea.setCaretPosition(messageOutputArea.getText().length());   //scroll down
        }

        /**
         * Writes error message to log file
         *
         * @param line a error string
         */
        public static void logError(String line)
        {
            myLogWriter.log("Error: " + line);
        }

        /**
         * Utility method used for posting output messages to the GUI output area
         *
         * @param line a error string
         */
        public static void postOutput(String line)  //write a line of output to out window
        {
            final String fLine = line;
            Runnable doAppendJTextArea = new Runnable()    //to prevent gui lockup
            {
                public void run() 
                {
                    myLogWriter.log("Output: " + fLine);
                    messageOutputArea.append(fLine);   //output console line
                    // Scroll down as text is output
                    messageOutputArea.setCaretPosition(messageOutputArea.getText().length());
                }
            };
            SwingUtilities.invokeLater(doAppendJTextArea);
        }

        /**
         * Simply Displays a beta warning message, it normally shouldn't be called
         * @param the jframe to display the popup 
         */
        private static void DisplayBetaWarning(JFrame frame )
        {
            Object[] options = {"Okay","No Thanks"};   //button titles
            int answer = JOptionPane.showOptionDialog(frame, 
                    "You are testing a pre-release version of OpenLV, intended for testing purposes only" + endL +
                    "Please report bugs to the person that you obtained this software from." + endL +
                    "Please do not re-distribute this software." + endL +
                    "Click 'Okay' to acknowledge these terms",
                    "Beta Version Disclaimer",
                    JOptionPane.OK_OPTION,
                    JOptionPane.ERROR_MESSAGE,
                    null,              //no custom icon
                    options,           //the titles of buttons
                    options[0]);       //default button title

            if(answer != JOptionPane.OK_OPTION)   //user clicked ok button
            {
                System.exit(1);
            }
        }

        //aux funtion used for troubleshooting, simply displays system properties
        private static String getSystemProps()
        {
            String ret = null;

            Properties prop = System.getProperties();
            Enumeration keys = prop.keys();
            while (keys.hasMoreElements()) {
                String key = (String)keys.nextElement();
                String value = (String)prop.get(key);
                System.err.println(key + ": " + value);
                postOutput(key + ": " + value + "\n");
		ret = ret + key + ": " + value + "\n";

           }

	   return ret;
        }

        /**
         * Checks the user editable conf file (outside the jar) for a config string, if not found, uses default from jar file properties file
         * @param str the string (actually substring eg for OpenLVLauncher.defaultRamSize use defaultRamSize) 
	 * @return the conf string if it exists
         */
	private static String getConfString(String str){
		String theString = null;
		if(ecs.getString("OpenLV." + str).equals("notfound")){
			theString = InternalConfigStrings.getString("OpenLVLauncher." + str);
		}
		else{
			theString = ecs.getString("OpenLV." + str);
		}

		return theString;

	}
        /**
         * convenience function to see if this is a linux host 
	 * @return true if this is a linxu host 
         */
	public static boolean isLinux(){
		return thisComputerOSType.equals("LINUX");
        }
        /**
         * convenience function to see if this is a windows host 
	 * @return true if this is a windows host 
         */
	public static boolean isWindows(){
		return thisComputerOSType.equals("WINDOWS");
        }
        /**
         * convenience function to see if this is a vmware host 
	 * @return true if this is a vmware host 
         */
	public static boolean isVMWare(){
		return virtualizationSoftware.equals("VMWare");
        }
        /**
         * convenience function to see if this is a virtualbox host 
	 * @return true if this is a virtualbox host 
         */
	public static boolean isVirtualBox(){
		return virtualizationSoftware.equals("VirtualBox");
        }



    }

