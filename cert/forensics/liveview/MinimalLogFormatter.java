package cert.forensics.liveview;
/*
 * A log formatter that removes all of the metadata typically associated with a log
 * entry and simply prints the log messages (for readability)
 * 
 * Author: 	Brian Kaplan
 * 			bfkaplan@cmu.edu
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
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class MinimalLogFormatter extends Formatter
{
	// This method is called for every log records
	public String format(LogRecord rec)
	{
		return formatMessage(rec) + System.getProperty("line.separator");
	}

}
