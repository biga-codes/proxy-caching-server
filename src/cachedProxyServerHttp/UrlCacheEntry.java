package cachedProxyServerHttp;

class UrlCacheEntry {
  String url;
  MimeHeader mh;
  byte data[];
  int length = 0;

  public UrlCacheEntry(String u, MimeHeader m) {
    url = u;
    mh = m;
    String cl = mh.get("Content-Length");
    if (cl != null) {
      data = new byte[Integer.parseInt(cl)];
    }
  }

  void append(byte d[], int n) {
    if (n <= 0) {
      return;
    }
    if (data == null) {
      data = new byte[n];
      System.arraycopy(d, 0, data, 0, n);
      length = n;
    } else if (length + n > data.length) {
      byte old[] = data;
      data = new byte[old.length + n];
      System.arraycopy(old, 0, data, 0, old.length);
      System.arraycopy(d, 0, data, old.length, n);
      length += n;
    } else {
      System.arraycopy(d, 0, data, length, n);
      length += n;
    }
  }
}
