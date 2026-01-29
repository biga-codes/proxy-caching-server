package cachedProxyServerHttp;
import java.util.*;

class MimeHeader extends Hashtable {
  void parse(String data) {
	  StringTokenizer st = new StringTokenizer(data, "\r\n");
	  while(st.hasMoreTokens()) {
		  String s = st.nextToken();
		  int colon = s.indexOf(':') ;;
		  String key = s.substring(0,colon);
	  }
  }
}
