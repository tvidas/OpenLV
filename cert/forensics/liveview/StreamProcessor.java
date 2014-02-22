package cert.forensics.liveview;
/*
 * This is a thread used to consume the output of an external process in
 * a separate thread to avoid conflicts/deadlock and for performance reasons.
 * 
 * Brian Kaplan
 * bfkaplan@andrew.cmu.edu
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class StreamProcessor extends Thread
{
    InputStream is;
    String type;
    
    StreamProcessor(InputStream is, String type)
    {
        this.is = is;
        this.type = type;
    }
    
    public void run()
    {
        try
        {
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String line = null;
            String separator = System.getProperty("line.separator");
            while ( (line = br.readLine()) != null)
            {
            	if(line.length() > 0 && line.charAt(line.length() - 1) != '\n')
            		line = line + separator;
            	if(type == "ERROR")
            	{
            		LiveViewLauncher.postError(line);
            	}	
            	else
            	{}
            }
        } 
    	catch (IOException ioe)
        {
            ioe.printStackTrace();  
        }
    }
}
