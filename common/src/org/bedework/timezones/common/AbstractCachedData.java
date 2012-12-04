/* ********************************************************************
    Licensed to Jasig under one or more contributor license
    agreements. See the NOTICE file distributed with this work
    for additional information regarding copyright ownership.
    Jasig licenses this file to you under the Apache License,
    Version 2.0 (the "License"); you may not use this file
    except in compliance with the License. You may obtain a
    copy of the License at:

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on
    an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied. See the License for the
    specific language governing permissions and limitations
    under the License.
*/
package org.bedework.timezones.common;

import edu.rpi.cmt.calendar.IcalToXcal;
import edu.rpi.cmt.calendar.XcalUtil;
import edu.rpi.cmt.timezones.model.TimezoneType;
import edu.rpi.sss.util.DateTimeUtil;
import edu.rpi.sss.util.FlushMap;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.UnfoldingReader;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.property.LastModified;
import net.fortuna.ical4j.model.property.TzId;

import org.apache.log4j.Logger;

import ietf.params.xml.ns.icalendar_2.IcalendarType;

import java.io.StringReader;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;

/** Abstract class to help simplify implementation
 *
 * @author douglm
 */
public abstract class AbstractCachedData implements CachedData {
  protected boolean debug;

  protected transient Logger log;

  protected String msgPrefix;

  /** When we were created for debugging */
  protected Timestamp objTimestamp;

  /** XML formatted UTC dtstamp (i.e. separators) for the data */
  protected String dtstamp;

  private Map<String, String> vtzs = new HashMap<String, String>();

  private Map<String, TimeZone> timeZones = new FlushMap<String, TimeZone>();

  private Map<String, IcalendarType> xtzs = new HashMap<String, IcalendarType>();

  private Map<String, String> aliasedVtzs = new HashMap<String, String>();

//  private Map<String, TimeZone> aliasedTzs = new HashMap<String, TimeZone>();

  private Map<String, IcalendarType> aliasedXtzs = new HashMap<String, IcalendarType>();

  private SortedSet<String> nameList;

  protected Map<ExpandedMapEntryKey, ExpandedMapEntry> expansions =
    new HashMap<ExpandedMapEntryKey, ExpandedMapEntry>();

  /** */
  public static class AliasMaps {
    /** */
    public String aliasesStr;

    /** */
    public Properties aliases;

    /** */
    public Map<String, SortedSet<String>> byTzid;
    /** */
    public Map<String, String> byAlias;
  }

  protected AliasMaps aliasMaps;

  private List<TimezoneType> timezones;

  /**
   * @param msgPrefix - for messages
   * @throws TzException
   */
  public AbstractCachedData(final String msgPrefix) throws TzException {
    debug = getLogger().isDebugEnabled();

    this.msgPrefix = msgPrefix;
  }

  @Override
  public List<Stat> getStats() throws TzException {
    List<Stat> stats = new ArrayList<Stat>();

    if (vtzs == null) {
      stats.add(new Stat(msgPrefix, " #tzs  Unavailable"));
    } else {
      stats.add(new Stat(msgPrefix + " #tzs", String.valueOf(vtzs.size())));
    }

    stats.add(new Stat(msgPrefix + " dtstamp", dtstamp));
    stats.add(new Stat(msgPrefix + " cached expansions",
                       String.valueOf(expansions.size())));

    return stats;
  }

  /** Find tz identifiers or alias names that (partially) match the given value
   * @param val
   * @return list of strings - never null
   * @throws TzException
   */
  public abstract List<String> findIds(String val) throws TzException;

  /* ====================================================================
   *                   CachedData methods
   * ==================================================================== */

  /* (non-Javadoc)
   * @see org.bedework.timezones.common.CachedData#getDtstamp()
   */
  @Override
  public String getDtstamp() throws TzException {
    return dtstamp;
  }

  /* (non-Javadoc)
   * @see org.bedework.timezones.common.CachedData#fromAlias(java.lang.String)
   */
  @Override
  public String fromAlias(final String val) throws TzException {
    return aliasMaps.byAlias.get(val);
  }

  /* (non-Javadoc)
   * @see org.bedework.timezones.common.CachedData#getAliasesStr()
   */
  @Override
  public String getAliasesStr() throws TzException {
    return aliasMaps.aliasesStr;
  }

  /* (non-Javadoc)
   * @see org.bedework.timezones.common.CachedData#findAliases(java.lang.String)
   */
  @Override
  public SortedSet<String> findAliases(final String tzid) throws TzException {
    return aliasMaps.byTzid.get(tzid);
  }

  /* (non-Javadoc)
   * @see org.bedework.timezones.common.CachedData#getNameList()
   */
  @Override
  public SortedSet<String> getNameList() throws TzException {
    return nameList;
  }

  @Override
  public void setExpanded(final ExpandedMapEntryKey key,
                          final ExpandedMapEntry tzs) throws TzException {
    expansions.put(key, tzs);
  }

  @Override
  public ExpandedMapEntry getExpanded(final ExpandedMapEntryKey key) throws TzException {
    return expansions.get(key);
  }

  /* (non-Javadoc)
   * @see org.bedework.timezones.common.CachedData#getCachedVtz(java.lang.String)
   */
  @Override
  public String getCachedVtz(final String name) throws TzException {
    return vtzs.get(name);
  }

  /* (non-Javadoc)
   * @see org.bedework.timezones.common.CachedData#getAllCachedVtzs()
   */
  @Override
  public Collection<String> getAllCachedVtzs() throws TzException {
    return vtzs.values();
  }

  /* (non-Javadoc)
   * @see org.bedework.timezones.common.CachedData#getTimeZone(java.lang.String)
   */
  @Override
  public TimeZone getTimeZone(final String tzid) throws TzException {
    TimeZone tz = timeZones.get(tzid);

    if (tz != null) {
      return tz;
    }

    net.fortuna.ical4j.model.Calendar cal = parseDef(TzServerUtil.getCalHdr() +
                                                     getCachedVtz(tzid) +
                                                     TzServerUtil.getCalTlr());

    tz = new TimeZone(vtzFromCal(cal));


    timeZones.put(tzid, tz);

    return tz;
  }

  /* (non-Javadoc)
   * @see org.bedework.timezones.common.CachedData#getAliasedTimeZone(java.lang.String)
   * /
  @Override
  public TimeZone getAliasedTimeZone(final String tzid) throws TzException {
    return aliasedTzs.get(tzid);
  }*/

  @Override
  public IcalendarType getXTimeZone(final String tzid) throws TzException {
    return xtzs.get(tzid);
  }

  @Override
  public IcalendarType getAliasedXTimeZone(final String tzid) throws TzException {
    return aliasedXtzs.get(tzid);
  }

  /* (non-Javadoc)
   * @see org.bedework.timezones.common.CachedData#getAliasedCachedVtz(java.lang.String)
   */
  @Override
  public String getAliasedCachedVtz(final String name) throws TzException {
    return aliasedVtzs.get(name);
  }

  /* (non-Javadoc)
   * @see org.bedework.timezones.common.CachedData#getSummaries(java.lang.String)
   */
  @Override
  public List<TimezoneType> getTimezones(final String changedSince) throws TzException {
    if (changedSince == null) {
      return timezones;
    }

    List<TimezoneType> ss = new ArrayList<TimezoneType>();

    for (TimezoneType tz: timezones) {
      if (tz.getLastModified() == null) {
        ss.add(tz);
        continue;
      }

      String lm = DateTimeUtil.rfcDateTimeUTC(tz.getLastModified());

      /*
       * cs > lm +
       * cs = lm 0
       * cs < lm -
       */

      if (changedSince.compareTo(lm) < 0) {
        ss.add(tz);
      }
    }

    return ss;
  }

  @Override
  public List<TimezoneType> findTimezones(final String name) throws TzException {
    List<TimezoneType> sums = new ArrayList<TimezoneType>();

    List<String> ids = findIds(name);

    for (TimezoneType tz: timezones) {
      if (ids.contains(tz.getTzid())) {
        sums.add(tz);
      }
    }

    return sums;
  }

  /* ====================================================================
   *                   protected methods
   * ==================================================================== */

  /**
   * @param id
   * @param caldef a tz spec in the form of a CALENDAR component
   * @param storedDtstamp
   * @throws TzException
   */
  protected void processSpec(final String id,
                             final String caldef,
                             final String storedDtstamp) throws TzException {
    try {
      net.fortuna.ical4j.model.Calendar cal = parseDef(caldef);

      nameList.add(id);

      VTimeZone vtz = vtzFromCal(cal);

      vtzs.put(id, vtz.toString());

      /* Now build the XML version */

      IcalendarType xcal = IcalToXcal.fromIcal(cal, null, true);

      xtzs.put(id, xcal);

      /* ================== Build summary info ======================== */
      TimezoneType tz = new TimezoneType();

      tz.setTzid(id);

      LastModified lm = vtz.getLastModified();
      if (lm!= null) {
        tz.setLastModified(DateTimeUtil.fromRfcDateTimeUTC(lm.getValue()));
      } else if (storedDtstamp != null) {
        tz.setLastModified(DateTimeUtil.fromRfcDateTimeUTC(XcalUtil.getXmlFormatDateTime(storedDtstamp)));
      } else {
        tz.setLastModified(DateTimeUtil.fromRfcDateTimeUTC(dtstamp));
      }

      SortedSet<String> aliases = findAliases(id);

      // XXX Need to have list of local names per timezone
      //String ln = vtz.
      if (aliases != null) {
        for (String a: aliases) {
          if (tz.getAliases() == null) {
            tz.setAliases(new ArrayList<String>());
          }
          tz.getAliases().add(a);

          VTimeZone avtz = addAlias(a, vtz);

          cal.getComponents().clear();
          cal.getComponents().add(avtz);

          xcal = IcalToXcal.fromIcal(cal, null, true);

          aliasedXtzs.put(id, xcal);
        }
      }

      timezones.add(tz);
    } catch (TzException te) {
      throw te;
    } catch (Throwable t) {
      throw new TzException(t);
    }
  }

  protected net.fortuna.ical4j.model.Calendar parseDef(final String caldef) throws TzException {
    try {
      CalendarBuilder cb = new CalendarBuilder();

      UnfoldingReader ufrdr = new UnfoldingReader(new StringReader(caldef), true);

      return cb.build(ufrdr);
    } catch (Throwable t) {
      throw new TzException(t);
    }
  }

  protected VTimeZone vtzFromCal(final net.fortuna.ical4j.model.Calendar cal) throws TzException {
    VTimeZone vtz = (VTimeZone)cal.getComponents().getComponent(Component.VTIMEZONE);
    if (vtz == null) {
      throw new TzException("Incorrectly stored timezone");
    }

    return vtz;
  }

  protected void resetTzs() {
    nameList = new TreeSet<String>();
    timezones = new ArrayList<TimezoneType>();
  }

  /* Construct a new vtimezone with the alias as id and then
   * add it and the string version to the alias table.
   */
  protected VTimeZone addAlias(final String alias,
                          final VTimeZone vtz) throws TzException {
    try {
      VTimeZone avtz = (VTimeZone)vtz.copy();

      TzId tzid = avtz.getTimeZoneId();
      tzid.setValue(alias);

//      aliasedTzs.put(alias, new TimeZone(avtz));
      aliasedVtzs.put(alias, avtz.toString());

      return avtz;
    } catch (Throwable t) {
      throw new TzException(t);
    }
  }

  /* ====================================================================
   *                   private methods
   * ==================================================================== */

  /**
   * @return Logger
   */
  protected Logger getLogger() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }

  /**
   * @param t
   */
  protected void error(final Throwable t) {
    getLogger().error(this, t);
  }

  /**
   * @param msg
   */
  protected void error(final String msg) {
    getLogger().error(msg);
  }

  /**
   * @param msg
   */
  protected void warn(final String msg) {
    getLogger().warn(msg);
  }

  /**
   * @param msg
   */
  protected void info(final String msg) {
    getLogger().info(msg);
  }

  /**
   * @param msg
   */
  protected void trace(final String msg) {
    getLogger().debug(msg);
  }
}
