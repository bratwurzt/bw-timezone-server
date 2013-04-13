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
package org.bedework.timezones.server;

import org.bedework.timezones.common.TzServerUtil;

import edu.rpi.cmt.timezones.model.ErrorResponseType;

import org.apache.log4j.Logger;

import java.io.OutputStream;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.StringTokenizer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * @author douglm
 *
 */
public abstract class MethodBase {
  protected static final ErrorResponseType invalidTzid =
      new ErrorResponseType("invalid-tzid",
                            "The \"tzid\" query parameter is not present, or" +
                            " appears more than once.");

  protected static final ErrorResponseType missingTzid =
      new ErrorResponseType("missing-tzid",
                            "The \"tzid\" query parameter value does not map " +
                            "to a timezone identifier known to the server.");

  protected static final ErrorResponseType invalidStart =
      new ErrorResponseType("invalid-start",
                            "The \"start\" query parameter has an incorrect" +
                            " value, or appears more than once.");

  protected static final ErrorResponseType invalidEnd =
      new ErrorResponseType("invalid-end",
                            "The \"end\" query parameter has an incorrect " +
                            "value, or appears more than once, or has a value" +
                            " less than our equal to the \"start\" query " +
                            "parameter.");

  protected static final ErrorResponseType invalidChangedsince =
      new ErrorResponseType("invalid-changedsince",
                            "The \"changedsince\" query parameter has an " +
                            "incorrect value, or appears more than once.");

  protected boolean debug;

  protected transient Logger log;

  protected ObjectMapper mapper = new ObjectMapper(); // create once, reuse

  protected TzServerUtil util;

  /**
   * @param debug
   * @throws ServletException
   */
  public MethodBase(final boolean debug) throws ServletException {
    this.debug = debug;

    try {
      if (debug) {
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
      }

      DateFormat df = new SimpleDateFormat("yyyy'-'MM'-'dd'T'HH':'mm':'ss'Z'");

      mapper.setDateFormat(df);

      mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

      util = TzServerUtil.getInstance();
    } catch (Throwable t) {
      throw new ServletException(t);
    }
  }

  /**
   * @param req
   * @param resp
   * @throws ServletException
   */
  public abstract void doMethod(HttpServletRequest req,
                                HttpServletResponse resp)
        throws ServletException;

  /** Get the decoded and fixed resource URI
   *
   * @param req      Servlet request object
   * @return String  fixed up uri
   * @throws ServletException
   */
  public String getResourceUri(final HttpServletRequest req)
      throws ServletException {
    String uri = req.getServletPath();

    if ((uri == null) || (uri.length() == 0)) {
      /* No path specified - set it to root. */
      uri = "/";
    }

    if (debug) {
      trace("uri: " + uri);
    }

    String resourceUri = fixPath(uri);

    if (debug) {
      trace("resourceUri: " + resourceUri);
    }

    return resourceUri;
  }

  /** Return a path, beginning with a "/", after "." and ".." are removed.
   * If the parameter path attempts to go above the root we return null.
   *
   * Other than the backslash thing why not use URI?
   *
   * @param path      String path to be fixed
   * @return String   fixed path
   * @throws ServletException
   */
  public static String fixPath(final String path) throws ServletException {
    if (path == null) {
      return null;
    }

    String decoded;
    try {
      decoded = URLDecoder.decode(path, "UTF8");
    } catch (Throwable t) {
      throw new ServletException("bad path: " + path);
    }

    if (decoded == null) {
      return (null);
    }

    /** Make any backslashes into forward slashes.
     */
    if (decoded.indexOf('\\') >= 0) {
      decoded = decoded.replace('\\', '/');
    }

    /** Ensure a leading '/'
     */
    if (!decoded.startsWith("/")) {
      decoded = "/" + decoded;
    }

    /** Remove all instances of '//'.
     */
    while (decoded.indexOf("//") >= 0) {
      decoded = decoded.replaceAll("//", "/");
    }

    if (decoded.indexOf("/.") < 0) {
      return decoded;
    }

    /** Somewhere we may have /./ or /../
     */

    StringTokenizer st = new StringTokenizer(decoded, "/");

    ArrayList<String> al = new ArrayList<String>();
    while (st.hasMoreTokens()) {
      String s = st.nextToken();

      if (s.equals(".")) {
        // ignore
      } else if (s.equals("..")) {
        // Back up 1
        if (al.size() == 0) {
          // back too far
          return null;
        }

        al.remove(al.size() - 1);
      } else {
        al.add(s);
      }
    }

    /** Reconstruct */
    StringBuilder sb = new StringBuilder();
    for (String s: al) {
      sb.append('/');
      sb.append(s);
    }

    return sb.toString();
  }

  /** ===================================================================
   *                   Json methods
   *  =================================================================== */

  protected void writeJson(final OutputStream out,
                           final Object val) throws ServletException {
    try {
      mapper.writeValue(out, val);
    } catch (Throwable t) {
      throw new ServletException(t);
    }
  }

  /** ===================================================================
   *                   Logging methods
   *  =================================================================== */

  /**
   * @return Logger
   */
  protected Logger getLogger() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }

  protected void debugMsg(final String msg) {
    getLogger().debug(msg);
  }

  protected void error(final Throwable t) {
    getLogger().error(this, t);
  }

  protected void error(final String msg) {
    getLogger().error(msg);
  }

  protected void warn(final String msg) {
    getLogger().warn(msg);
  }

  protected void logIt(final String msg) {
    getLogger().info(msg);
  }

  protected void trace(final String msg) {
    getLogger().debug(msg);
  }
}