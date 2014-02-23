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
package cert.forensics.liveview;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * MinimalLogFormatter 
 * A log formatter that removes all of the metadata typically associated with a log
 * entry and simply prints the log messages (for readability)
 *
 * @author Tim Vidas
 * @author Brian Kaplan
 * @version 0.7, Jan 2009
 */

public class MinimalLogFormatter extends Formatter
{
    /**
     * Just a simple format wrapper, this method is called for every log record
     * @param rec the record to be formatted
     * @return a formatted log string
     */
    public String format(LogRecord rec)
    {
        return formatMessage(rec) + System.getProperty("line.separator");
    }

}
