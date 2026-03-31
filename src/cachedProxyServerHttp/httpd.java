package cachedProxyServerHttp;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

class httpd implements Runnable, LogMessage {
  private int port;
  private String docRoot;
  private LogMessage log;

  private Hashtable cache = new Hashtable();
  private boolean stopFlag;

  private static String version = "1.0";
  private static String mime_text_html = "text/html";
  private static String CRLF = "\r\n";
  private static String indexfile = "index.html";
  private static int buffer_size = 8192;

  static String mt[] = {
    "txt", "text/plain",
    "html", mime_text_html,
    "htm", "text/html",
    "gif", "image/gif",
    "jpg", "image/jpg",
    "jpeg", "image/jpg",
    "class", "application/octet-stream"
  };

  static String defaultExt = "txt";
  static Hashtable types = new Hashtable();

  static {
    for (int i = 0; i < mt.length; i += 2) {
      types.put(mt[i], mt[i + 1]);
    }
  }

  static String fnameToMimeType(String filename) {
    if (filename.endsWith("/")) {
      return mime_text_html;
    }
    int dot = filename.lastIndexOf('.');
    String ext = (dot > 0) ? filename.substring(dot + 1) : defaultExt;
    String ret = (String) types.get(ext);
    return ret != null ? ret : (String) types.get(defaultExt);
  }

  int hits_served = 0;
  int bytes_served = 0;
  int files_in_cache = 0;
  int bytes_in_cache = 0;
  int hits_to_cache = 0;

  private final byte toBytes(String s)[] {
    byte b[] = s.getBytes();
    return b;
  }

  private MimeHeader makeMimeHeader(String type, int length) {
    MimeHeader mh = new MimeHeader();
    Date curDate = new Date();
    TimeZone gmtTz = TimeZone.getTimeZone("GMT");
    SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy hh:mm:ss zzz");
    sdf.setTimeZone(gmtTz);
    mh.put("Date", sdf.format(curDate));
    mh.put("Server", "JavaCompleteReference/" + version);
    mh.put("Content-Type", type);
    if (length >= 0) {
      mh.put("Content-Length", String.valueOf(length));
    }
    return mh;
  }

  private String error(int code, String msg, String url) {
    String html_page = "<body>" + CRLF +
      "<h1>" + code + " " + msg + "</h1>" + CRLF +
      url + CRLF +
      "</body>" + CRLF;
    HttpResponse hr = new HttpResponse(code, msg,
      makeMimeHeader(mime_text_html, html_page.length()));
    return hr + html_page;
  }

  private String getRawRequest(InputStream in) throws IOException {
    byte buf[] = new byte[buffer_size];
    int pos = 0;
    int c;
    int state = 0;

    while ((c = in.read()) >= 0) {
      if (pos >= buf.length) {
        byte old[] = buf;
        buf = new byte[old.length + buffer_size];
        System.arraycopy(old, 0, buf, 0, old.length);
      }

      buf[pos++] = (byte) c;

      switch (state) {
        case 0:
          if (c == '\r') {
            state = 1;
          } else if (c == '\n') {
            state = 3;
          }
          break;
        case 1:
          state = (c == '\n') ? 2 : 0;
          break;
        case 2:
          if (c == '\r') {
            state = 4;
          } else if (c == '\n') {
            state = 5;
          } else {
            state = 0;
          }
          break;
        case 3:
          state = (c == '\n') ? 5 : 0;
          break;
        case 4:
          state = (c == '\n') ? 5 : 0;
          break;
        default:
          break;
      }

      if (state == 5) {
        return new String(buf, 0, pos);
      }
    }

    return (pos > 0) ? new String(buf, 0, pos) : null;
  }

  static String months[] = {
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
  };

  private String host;

  private final String fmt02d(int i) {
    if (i < 0) {
      i = -i;
      return ((i < 9) ? "-0" : "-") + i;
    }
    return ((i < 9) ? "0" : "") + i;
  }

  private void logEntry(String cmd, String url, int code, int size) {
    Calendar calendar = Calendar.getInstance();
    int tzmin = calendar.get(Calendar.ZONE_OFFSET) / (60 * 1000);
    int tzhour = tzmin / 60;
    tzmin -= tzhour * 60;

    log.log(host + " - - [" +
      fmt02d(calendar.get(Calendar.DATE)) + "/" +
      months[calendar.get(Calendar.MONTH)] + "/" +
      calendar.get(Calendar.YEAR) + ":" +
      fmt02d(calendar.get(Calendar.HOUR)) + ":" +
      fmt02d(calendar.get(Calendar.MINUTE)) + ":" +
      fmt02d(calendar.get(Calendar.SECOND)) + " " +
      fmt02d(tzhour) + fmt02d(tzmin) +
      "] \"" +
      cmd + " " +
      url + " HTTP/1.0\" " +
      code + " " +
      size + "\n");

    hits_served++;
    bytes_served += size;
  }

  private void writeString(OutputStream out, String s) throws IOException {
    out.write(toBytes(s));
  }

  private void writeUCE(OutputStream out, UrlCacheEntry uce) throws IOException {
    HttpResponse hr = new HttpResponse(200, "OK", uce.mh);
    writeString(out, hr.toString());
    out.write(uce.data, 0, uce.length);
    logEntry("GET", uce.url, 200, uce.length);
  }

  private synchronized boolean serveFromCache(OutputStream out, String url) throws IOException {
    UrlCacheEntry uce = (UrlCacheEntry) cache.get(url);
    if (uce == null) {
      return false;
    }
    hits_to_cache++;
    writeUCE(out, uce);
    return true;
  }

  private synchronized UrlCacheEntry readFile(File f, String url) throws IOException {
    if (!f.exists() || !f.isFile()) {
      return null;
    }

    MimeHeader mh = makeMimeHeader(fnameToMimeType(f.getName()), (int) f.length());
    UrlCacheEntry uce = new UrlCacheEntry(url, mh);

    FileInputStream in = new FileInputStream(f);
    try {
      byte file_buf[] = new byte[buffer_size];
      int n;
      while ((n = in.read(file_buf)) > 0) {
        uce.append(file_buf, n);
      }
    } finally {
      in.close();
    }

    UrlCacheEntry old = (UrlCacheEntry) cache.put(url, uce);
    if (old == null) {
      files_in_cache++;
      bytes_in_cache += uce.length;
    } else {
      bytes_in_cache += (uce.length - old.length);
    }
    return uce;
  }

  private synchronized UrlCacheEntry loadFile(InputStream in, String url, MimeHeader mh) throws IOException {
    UrlCacheEntry uce = new UrlCacheEntry(url, mh);
    byte file_buf[] = new byte[buffer_size];
    int n;
    int remaining = -1;
    String cl = mh.get("Content-Length");
    if (cl != null) {
      try {
        remaining = Integer.parseInt(cl);
      } catch (NumberFormatException ignored) {
        remaining = -1;
      }
    }

    if (remaining >= 0) {
      while (remaining > 0 && (n = in.read(file_buf, 0, Math.min(file_buf.length, remaining))) > 0) {
        uce.append(file_buf, n);
        remaining -= n;
      }
    } else {
      while ((n = in.read(file_buf)) > 0) {
        uce.append(file_buf, n);
      }
    }

    UrlCacheEntry old = (UrlCacheEntry) cache.put(url, uce);
    if (old == null) {
      files_in_cache++;
      bytes_in_cache += uce.length;
    } else {
      bytes_in_cache += (uce.length - old.length);
    }

    return uce;
  }

  private void writeDiskCache(UrlCacheEntry uce) throws IOException {
    String path = docRoot + uce.url;
    String dir = path.substring(0, path.lastIndexOf('/'));
    dir = dir.replace('/', File.separatorChar);
    new File(dir).mkdirs();

    String nativePath = path.replace('/', File.separatorChar);
    FileOutputStream out = new FileOutputStream(nativePath);
    try {
      out.write(uce.data, 0, uce.length);
    } finally {
      out.close();
    }
  }

  private void handleProxy(OutputStream out, String url, MimeHeader inmh) {
    try {
      int start = url.indexOf("://") + 3;
      int path = url.indexOf('/', start);
      String site = url.substring(start, path).toLowerCase();
      int port = 80;
      String server_url = url.substring(path);
      int colon = site.indexOf(':');
      if (colon > 0) {
        port = Integer.parseInt(site.substring(colon + 1));
        site = site.substring(0, colon);
      }

      url = "/cache/" + site + ((port != 80) ? (":" + port) : "") + server_url;
      if (url.endsWith("/")) {
        url += indexfile;
      }

      if (!serveFromCache(out, url)) {
        if (readFile(new File((docRoot + url).replace('/', File.separatorChar)), url) != null) {
          serveFromCache(out, url);
          return;
        }

        Socket server = new Socket(site, port);
        InputStream server_in = server.getInputStream();
        OutputStream server_out = server.getOutputStream();

        String ua = inmh.get("User-Agent");
        if (ua == null) {
          ua = "JavaCompleteReferenceProxy/" + version;
        } else {
          ua = ua + " via JavaCompleteReferenceProxy/" + version;
        }
        inmh.put("User-Agent", ua);

        String req = "GET " + server_url + " HTTP/1.0" + CRLF + inmh + CRLF;
        writeString(server_out, req);

        String raw_request = getRawRequest(server_in);
        HttpResponse server_response = new HttpResponse(raw_request);
        writeString(out, server_response.toString());

        if (server_response.statusCode == 200) {
          UrlCacheEntry uce = loadFile(server_in, url, server_response.mh);
          out.write(uce.data, 0, uce.length);
          writeDiskCache(uce);
          logEntry("GET", site + server_url, 200, uce.length);
        }

        server_out.close();
        server.close();
      }
    } catch (IOException e) {
      log.log("Exception: " + e + "\n");
    }
  }

  private void handleGet(OutputStream out, String url, MimeHeader inmh) {
    String cacheUrl = url.endsWith("/") ? (url + indexfile) : url;
    String filename = docRoot + cacheUrl;

    try {
      if (!serveFromCache(out, cacheUrl)) {
        File f = new File(filename.replace('/', File.separatorChar));
        if (!f.exists()) {
          writeString(out, error(404, "Not Found", url));
          logEntry("GET", url, 404, 0);
          return;
        }

        UrlCacheEntry uce = readFile(f, cacheUrl);
        if (uce == null) {
          writeString(out, error(500, "Internal Error", url));
          logEntry("GET", url, 500, 0);
          return;
        }

        writeUCE(out, uce);
      }
    } catch (IOException e) {
      log.log("Exception: " + e + "\n");
    }
  }

  private void doRequest(Socket s) throws IOException {
    InputStream in = s.getInputStream();
    OutputStream out = s.getOutputStream();

    String request = getRawRequest(in);
    if (request == null || request.length() == 0) {
      in.close();
      out.close();
      return;
    }

    int fsp = request.indexOf(' ');
    int nsp = request.indexOf(' ', fsp + 1);
    int eol = request.indexOf('\n');

    if (fsp <= 0 || nsp <= fsp || eol <= nsp) {
      writeString(out, error(400, "Bad Request", "Malformed request"));
      in.close();
      out.close();
      return;
    }

    String method = request.substring(0, fsp);
    String url = request.substring(fsp + 1, nsp);
    String raw_mime_header = request.substring(eol + 1);
    MimeHeader inmh = new MimeHeader(raw_mime_header);

    if (method.equalsIgnoreCase("get")) {
      if (url.indexOf("://") >= 0) {
        handleProxy(out, url, inmh);
      } else {
        handleGet(out, url, inmh);
      }
    } else {
      writeString(out, error(405, "Method Not Allowed", method));
    }

    in.close();
    out.close();
  }

  public void run() {
    try {
      ServerSocket acceptSocket = new ServerSocket(port);
      acceptSocket.setSoTimeout(1000);

      while (!stopFlag) {
        try {
          Socket s = acceptSocket.accept();
          host = s.getInetAddress().getHostName();
          doRequest(s);
          s.close();
        } catch (SocketTimeoutException ignored) {
        }
      }

      acceptSocket.close();
    } catch (IOException e) {
      log.log("accept loop IOException: " + e + "\n");
    } catch (Exception e) {
      log.log("Exception: " + e + "\n");
    }
  }

  private Thread t;

  public synchronized void start() {
    stopFlag = false;
    if (t == null) {
      t = new Thread(this);
      t.start();
    }
  }

  public synchronized void stop() {
    stopFlag = true;
    t = null;
    log.log("Stopped at " + new Date() + "\n");
  }

  public httpd(int p, String dr, LogMessage lm) {
    port = p;
    docRoot = dr;
    log = (lm != null) ? lm : this;
  }

  public static void main(String args[]) {
    httpd h = new httpd(80, "c:\\www", null);
    h.log = h;
    h.start();
    try {
      Thread.currentThread().join();
    } catch (InterruptedException e) {
    }
  }

  public void log(String m) {
    System.out.print(m);
  }
}
