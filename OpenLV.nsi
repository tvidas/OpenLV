!addplugindir "Inetc\Plugins"
!include "WordFunc.nsh"
!insertmacro VersionCompare

!define PRODUCT_NAME "OpenLV"
!define PRODUCT_VERSION "0.9.2"

!define PRODUCT_EDITION "Open"
!define BASE_DIRECTORY "${PRODUCT_VERSION}\OpenLV_${PRODUCT_VERSION}_${PRODUCT_EDITION}"

!define PRODUCT_PUBLISHER "OpenLV Dev Team"
!define PRODUCT_WEB_SITE "http://www.openlv.org"
!define PRODUCT_DIR_REGKEY "Software\Microsoft\Windows\CurrentVersion\App Paths\OpenLV${PRODUCT_EDITION}.jar"
!define PRODUCT_UNINST_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_NAME}"
!define PRODUCT_UNINST_ROOT_KEY "HKLM"

;java auto-download
!define JRE_MIN_VERSION "1.5"
;!define JRE_URL "http://dlc.sun.com/jdk/jre-1_5_0_01-windows-i586-p.exe"
;this is jre-6u20-windows-i586-p.exe
;!define JRE_URL "http://javadl.sun.com/webapps/download/AutoDL?BundleId=39494"
;!define JRE_URL "http://download.oracle.com/otn-pub/java/jdk/6u43-b01/jre-6u43-windows-i586.exe"
!define JRE_URL "https://edelivery.oracle.com/otn-pub/java/jdk/6u43-b01/jre-6u43-windows-i586.exe"
;vmware auto-download
!define VMWARE_SERVER_URL "http://download3.vmware.com/software/vmserver/VMware-server-installer-1.0.10-203137.exe"
;!define VMWARE_SERVER_URL "http://download3.vmware.com/software/vmserver/VMware-server-installer-1.0.8-126538.exe"
;!define VMWARE_SERVER_URL "http://download3.vmware.com/software/vmserver/VMware-server-installer-1.0.3-44356.exe"
;!define VMWARE_SERVER_URL "http://download3.vmware.com/software/vmserver/VMware-server-installer-1.0.0-28343.exe"
;vmware disk mount auto-download
;vddk requires a log-in...
;!define VMWARE_MOUNT_URL "http://www.vmware.com/support/developer/vddk/"
;!define VMWARE_MOUNT_URL "http://download3.vmware.com/software/wkst/VMware-mount-5.5.0-18463.exe"


!define JRE_INSTALLER_NAME "jre-6u43-windows-i586.exe"
;!define JRE_INSTALLER_NAME "jre-6u17-windows-i586-s.exe"
!define VMWARE_INSTALLER_NAME "VMware-server-installer-1.0.10-203137.exe"
!define VMWARE_VDDK_INSTALLER_NAME "VMware-vix-disklib-1.1.1-207031.i386.exe"
;!define JRE_INSTALLER_NAME "jre-6u11-windows-i586-p.exe"
;!define VMWARE_INSTALLER_NAME "VMware-server-installer-1.0.8-126538.exe"
;!define VMWARE_VDDK_INSTALLER_NAME "VMware-vix-disklib-e.x.p-99191.i386.exe"

!include "MUI.nsh"
!define MUI_ABORTWARNING
!define MUI_ICON "${NSISDIR}\Contrib\Graphics\Icons\modern-install.ico"
!define MUI_UNICON "${NSISDIR}\Contrib\Graphics\Icons\modern-uninstall.ico"

; Welcome page
!insertmacro MUI_PAGE_WELCOME
; License page
!insertmacro MUI_PAGE_LICENSE "OpenLVLicense.txt"
; Directory page
!insertmacro MUI_PAGE_DIRECTORY
; Instfiles page
!insertmacro MUI_PAGE_INSTFILES
; Finish page
;!define MUI_FINISHPAGE_RUN "$SYSDIR\javaw.exe" "-jar $INSTDIR\OpenLV${PRODUCT_EDITION}.jar"
!insertmacro MUI_PAGE_FINISH

; Uninstaller pages
!insertmacro MUI_UNPAGE_INSTFILES

; Language files
!insertmacro MUI_LANGUAGE "English"

Name "${PRODUCT_NAME} ${PRODUCT_VERSION}"
OutFile "OpenLV${PRODUCT_EDITION}Installerv${PRODUCT_VERSION}.exe"
InstallDir "$PROGRAMFILES\${PRODUCT_NAME}"
InstallDirRegKey HKLM "${PRODUCT_DIR_REGKEY}" ""
ShowInstDetails show
ShowUnInstDetails show

Section "MainSection" SEC01

  Call DetectPreviousInstall
  Call DetectJRE
  Call DetectVMSoftware
  Call DetectOldVMWareMount
  Call DetectVDDK
  
  SetOutPath "$INSTDIR"
  SetOverwrite try
  File "${BASE_DIRECTORY}\OpenLV${PRODUCT_EDITION}.jar"
  CreateDirectory "$SMPROGRAMS\${PRODUCT_NAME}"

  Call FindJRE
  Pop $R0

  CreateShortCut "$DESKTOP\${PRODUCT_NAME} ${PRODUCT_VERSION}.lnk" $R0 "-jar OpenLV${PRODUCT_EDITION}.jar" "$INSTDIR\Resources\app.ico" 0
  CreateShortCut "$SMPROGRAMS\${PRODUCT_NAME}\${PRODUCT_NAME} ${PRODUCT_VERSION}.lnk" $R0 "-jar OpenLV${PRODUCT_EDITION}.jar" "$INSTDIR\Resources\app.ico" 0
  SetOutPath "$INSTDIR\Resources"
  File "${BASE_DIRECTORY}\Resources\generic.mbr"
  File "${BASE_DIRECTORY}\Resources\genericW98Me.mbr"
  File "${BASE_DIRECTORY}\Resources\app.ico"
  File "${BASE_DIRECTORY}\Resources\LVSmallIcon.gif"
  File "${BASE_DIRECTORY}\Resources\merge.reg"
SectionEnd

Section -AdditionalIcons
  SetOutPath $INSTDIR
  WriteIniStr "$INSTDIR\${PRODUCT_NAME}.url" "InternetShortcut" "URL" "${PRODUCT_WEB_SITE}"
  CreateShortCut "$SMPROGRAMS\${PRODUCT_NAME}\Website.lnk" "$INSTDIR\${PRODUCT_NAME}.url"
  CreateShortCut "$SMPROGRAMS\${PRODUCT_NAME}\Uninstall.lnk" "$INSTDIR\uninst.exe"
SectionEnd

Section -Post
  WriteUninstaller "$INSTDIR\uninst.exe"
  WriteRegStr HKLM "${PRODUCT_DIR_REGKEY}" "" "$INSTDIR\OpenLV${PRODUCT_EDITION}.jar"
  WriteRegStr ${PRODUCT_UNINST_ROOT_KEY} "${PRODUCT_UNINST_KEY}" "DisplayName" "$(^Name)"
  WriteRegStr ${PRODUCT_UNINST_ROOT_KEY} "${PRODUCT_UNINST_KEY}" "UninstallString" "$INSTDIR\uninst.exe"
  WriteRegStr ${PRODUCT_UNINST_ROOT_KEY} "${PRODUCT_UNINST_KEY}" "DisplayIcon" "$INSTDIR\Resources\app.ico"
  WriteRegStr ${PRODUCT_UNINST_ROOT_KEY} "${PRODUCT_UNINST_KEY}" "DisplayVersion" "${PRODUCT_VERSION}"
  WriteRegStr ${PRODUCT_UNINST_ROOT_KEY} "${PRODUCT_UNINST_KEY}" "URLInfoAbout" "${PRODUCT_WEB_SITE}"
  WriteRegStr ${PRODUCT_UNINST_ROOT_KEY} "${PRODUCT_UNINST_KEY}" "Publisher" "${PRODUCT_PUBLISHER}"
SectionEnd

Function GetJRE

        IfFileExists "$EXEDIR\${JRE_INSTALLER_NAME}" foundjre


        MessageBox MB_OK|MB_ICONINFORMATION "${PRODUCT_NAME} requires Java 1.5 or higher, it will now \
                         be downloaded and installed because it could not be detected on your system"

        StrCpy $2 "$TEMP\${JRE_INSTALLER_NAME}"
;        nsisdl::download /TIMEOUT=30000 ${JRE_URL} $2
        inetc::get /NOCANCEL /CONNECTTIMEOUT=30000 /RECEIVETIMEOUT=30000 /RESUME "" ${JRE_URL} $2
        Pop $R0 ;Get the return value
                StrCmp $R0 "OK" +3
                MessageBox MB_OK "Download failed: $R0"
                Quit
        ExecWait $2
        Delete $2
        Goto done
        
        foundjre:
        Messagebox MB_OK|MB_ICONINFORMATION "${PRODUCT_NAME} requires Java 1.5 or higher, it will now \
                                          be installed from your installation media because no compatible Java installation could be detected on your system"
        ExecWait "$EXEDIR\${JRE_INSTALLER_NAME}"
        
        done:
FunctionEnd

Function GetVMware

        IfFileExists "$EXEDIR\${VMWARE_INSTALLER_NAME}" foundvmware

        MessageBox MB_OK|MB_ICONINFORMATION "${PRODUCT_NAME} reqires either VMware Server 1.0+, VMware Workstation 5.5+ or VirtualBox 2.0+. The free VMware Server will \
                                          now be downloaded and installed because no compatible VMware installation could be detected on your system"

        StrCpy $2 "$TEMP\${VMWARE_INSTALLER_NAME}"
        nsisdl::download /TIMEOUT=30000 ${VMWARE_SERVER_URL} $2
        Pop $R0 ;Get the return value
                StrCmp $R0 "success" +3
                MessageBox MB_OK "Download failed: $R0"
                Quit
        ExecWait $2
        Delete $2
        Goto done
        
        foundvmware:
        Messagebox MB_OK|MB_ICONINFORMATION "${PRODUCT_NAME} reqires either VMware Server 1.0+ or VMware Workstation 5.5+. The free VMware Server will \
                                          now be installed from your installation media because no compatible VMware installation could be detected on your system"

        ExecWait "$EXEDIR\${VMWARE_INSTALLER_NAME}"
        
        done:
FunctionEnd

Function GetVDDK

        IfFileExists "$EXEDIR\${VMWARE_VDDK_INSTALLER_NAME}" foundvddk

        MessageBox MB_OK|MB_ICONINFORMATION "${PRODUCT_NAME} reqires VMware's Virtual Disk Developement Kit (VDDK) in order to use it's built in \
					Disk Mount Utility. This free utility must downloaded and installed because it could be detected on your system \
					Note:  Free Registration is required.  (http://www.vmware.com/download/sdk/virtualdisk.html)"

                Quit

        foundvddk:
        Messagebox MB_OK|MB_ICONINFORMATION "${PRODUCT_NAME} reqires VMware's VDDK Disk Mount Utility. This free utility will \
                                          now be installed from your installation media because no compatible VMware Disk Mount utility could be detected on your system"

        ExecWait "$EXEDIR\${VMWARE_VDDK_INSTALLER_NAME}"

        done:
FunctionEnd


Function DetectJRE
  ReadRegStr $2 HKLM "SOFTWARE\JavaSoft\Java Runtime Environment" \
             "CurrentVersion"
  
  ${VersionCompare} $2 ${JRE_MIN_VERSION} $R0
  
  IntCmp $R0 1 done
  IntCmp $R0 0 done

  Call GetJRE

  done:
FunctionEnd

Function DetectPreviousInstall
  ReadRegStr $2 HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_NAME}" \
             "UninstallString"
  StrCmp $2 "" done oldversiondetected

oldversiondetected:

    Messagebox MB_OK|MB_ICONINFORMATION "A previous version of ${PRODUCT_NAME} was detected on your system. \
                       Please uninstall it before installing the new version."

    Messagebox MB_YESNO "Would you like to pause this installation and launch the uninstaller now?" IDNO noskip
    ;execute the Uninstall info found in the registry
    ExecWait $2
	
    Messagebox MB_OK|MB_ICONINFORMATION "Please click 'OK' when the uninstaller has finished."	
  
    goto done
    noskip:
    ;Pop $0
    ;didn't want to unintall
    abort
  done:
FunctionEnd


Function FindJRE
;
;  Find JRE (javaw.exe)
;  1 - in .\jre directory (JRE Installed with application)
;  2 - in JAVA_HOME environment variable
;  3 - in the registry
;  4 - assume javaw.exe in current dir or PATH

  Push $R0
  Push $R1

  ClearErrors
  StrCpy $R0 "$EXEDIR\jre\bin\javaw.exe"
  IfFileExists $R0 JreFound
  StrCpy $R0 ""

  ClearErrors
  ReadEnvStr $R0 "JAVA_HOME"
  StrCpy $R0 "$R0\bin\javaw.exe"
  IfErrors 0 JreFound

  ClearErrors
  ReadRegStr $R1 HKLM "SOFTWARE\JavaSoft\Java Runtime Environment" \
             "CurrentVersion"
  ReadRegStr $R0 HKLM "SOFTWARE\JavaSoft\Java Runtime Environment\$R1" \
             "JavaHome"
  StrCpy $R0 "$R0\bin\javaw.exe"

  IfErrors 0 JreFound
  StrCpy $R0 "javaw.exe"

 JreFound:
  Pop $R1
  Exch $R0
FunctionEnd

Function DetectVMSoftware
  Call DetectVBox
  Pop $R0
  IntCmp $R0 1 done

  Call DetectVMWare
  Pop $R0
  IntCmp $R0 1 done

  Call GetVMware
 
  done:
FunctionEnd

Function DetectVBox
;TODO virtualbox registery settings
  StrCpy $R0 "0"
  ReadRegStr $2 HKLM "SOFTWARE\VMWare, Inc." \
             "Core"
  StrCmp $2 "VMware Server Standalone" ret1 done
  StrCmp $2 "VMware Workstation" ret1 done

  ret1:
  StrCpy $R0 "1"

  done:
FunctionEnd

Function DetectVMware
  StrCpy $R0 "0"
  ReadRegStr $2 HKLM "SOFTWARE\VMWare, Inc." \
             "Core"
  StrCmp $2 "VMware Server Standalone" ret1 done
  StrCmp $2 "VMware Workstation" ret1 done

  ret1:
  StrCpy $R0 "1"

  done:
FunctionEnd

Function DetectVDDK
  ReadRegStr $2 HKLM "SOFTWARE\VMware, Inc.\VMware Virtual Disk Development Kit" \
             "InstallPath"
  StrCmp $2 "" novddk done

  novddk:
  Call GetVDDK

  done:
FunctionEnd

Function DetectOldVMwareMount
  ReadRegStr $2 HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\vmware-mount.exe" \
             "Path"
  StrCmp $2 "" done hasvmmount

  hasvmmount:
;  Call UninstallVMwareMount
    Messagebox MB_YESNO "A legacy version of vmware-mount (VMWare Diskmount Utility) was detected \
			on your system.  This should be removed for proper OpenLV operation.  \
			You should install the newer VDDK version of vmware-mount.  \
			Would you like to launch the uninstaller for the Diskmount Utility now?" IDNO noskip
    ;this is the remove key for VMWare DiskMount 5.5 
    ExecWait "MsiExec.exe /I{601D774D-0D04-4CB1-9E3B-5394FAAFA1FB}"
    noskip:
    Pop $0

  done:
FunctionEnd

Function un.onUninstSuccess
  HideWindow
  MessageBox MB_ICONINFORMATION|MB_OK "$(^Name) was successfully removed from your computer."
FunctionEnd

Function un.onInit
  MessageBox MB_ICONQUESTION|MB_YESNO|MB_DEFBUTTON2 "Are you sure you want to completely remove $(^Name) and all of its components?" IDYES +2
  Abort
FunctionEnd

Section Uninstall
  Delete "$INSTDIR\${PRODUCT_NAME}.url"
  Delete "$INSTDIR\uninst.exe"
  Delete "$INSTDIR\TempWmicBatchFile.bat"
  Delete "$INSTDIR\MostRecentRun.log"
  Delete "$INSTDIR\Resources\merge.reg"
  Delete "$INSTDIR\Resources\app.ico"
  Delete "$INSTDIR\Resources\LVSmallIcon.gif"
  Delete "$INSTDIR\Resources\genericW98Me.mbr"
  Delete "$INSTDIR\Resources\generic.mbr"
  Delete "$INSTDIR\OpenLV${PRODUCT_EDITION}.jar"

  Delete "$SMPROGRAMS\${PRODUCT_NAME}\Uninstall.lnk"
  Delete "$SMPROGRAMS\${PRODUCT_NAME}\Website.lnk"
  Delete "$DESKTOP\${PRODUCT_NAME} ${PRODUCT_VERSION}.lnk"
  Delete "$SMPROGRAMS\${PRODUCT_NAME}\${PRODUCT_NAME} ${PRODUCT_VERSION}.lnk"

  RMDir "$SMPROGRAMS\${PRODUCT_NAME}"
  RMDir "$INSTDIR\Resources"
  RMDir "$INSTDIR"

  DeleteRegKey ${PRODUCT_UNINST_ROOT_KEY} "${PRODUCT_UNINST_KEY}"
  DeleteRegKey HKLM "${PRODUCT_DIR_REGKEY}"
  SetAutoClose true
SectionEnd
