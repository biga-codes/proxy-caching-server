

package cachedProxyServerHttp;
import java.util.*;

class MimeHeader extends Hashtable {
  void parse(String data) {
	  StringTokenizer st = new StringTokenizer(data, "\r\n");
	  while(st.hasMoreTokens()) {
		  String s = st.nextToken();
		  int colon = s.indexOf(':') ;;
		  String key = s.substring(0,colon);
		  String val = s.substring(colon+2);
		  put(key,val);
	  }
  }
  
  //cnstrctr
  MimeHeader(){}
  MimeHeader(String d){
	  parse(d);
  }
  
  public String toString() {
	  //each entry in the table built here
	  String ret = "";
	  Enumeration<?> e = keys();
	  
	  while(e.hasMoreElements()) {
		  String key = (String) e.nextElement();
		  String val = (String) get(key);
		  ret +=key + ": "+val +"\r\n";
	  }
	  
	  return ret;
  }
  
  /* we are converting the parsed string here to canonical frm to ensure no caps or format mismatch*/
  private String fix(String ms) {
  char chars[] = ms.toLowerCase().toCharArray();
  boolean upcase = true; 
  
   for(int i=0;i<chars.length-1;i++) {
	   char ch=chars[i];
	   if(upcase && 'a' <= ch && ch<='z') {
		   chars[i] = (char) (ch-('a'-'A'));
	   }
	   upcase=(ch=='-');
   }
   return new String(chars);
   
  
}
  @Override
  public String get(String key) {
	  return (String) super.get(fix(key)); //override hashtable parent
  }
  
  @Override
  public void put(String key, String val) {
	  super.put(fix(key),val); 
  }
}
