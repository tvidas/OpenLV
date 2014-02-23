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

/**
 * ComboBoxItem
 * Convenience class representing an item inside a combo box with display text and an underlying value
 * This is used so that a nice readable OS type can be displayed in a combo box while a less readable 
 * vmware guestos value is used internally.
 *
 * @author Tim Vidas
 * @author Brian Kaplan
 * @version 0.7, Jan 2009
 */

public class ComboBoxItem
{
    private String displayText;			//text displayed in the combo box dropdown
    private String underlyingValue;		//value used internally for that displayed text

    public ComboBoxItem(String displayText, String underlyingValue)
    {
        this.displayText = displayText;
        this.underlyingValue = underlyingValue;
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
