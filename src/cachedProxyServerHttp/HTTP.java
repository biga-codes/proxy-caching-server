package cachedProxyServerHttp;

import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;
import java.util.Date;

public class HTTP extends Applet implements LogMessage, ActionListener {
  int m_port = 80;
  String m_docroot = "c:\\www";

  TextArea m_log;
  Label status;
  httpd m_httpd;

  public void init() {
    String p = getParameter("port");
    if (p != null) {
      try {
        m_port = Integer.parseInt(p);
      } catch (NumberFormatException ignored) {
      }
    }

    String dr = getParameter("docroot");
    if (dr != null) {
      m_docroot = dr;
    }

    setLayout(new BorderLayout());

    Label title = new Label("JavaCompleteReference HTTP Proxy", Label.CENTER);
    title.setFont(new Font("SansSerif", Font.BOLD, 12));
    add("North", title);

    m_log = new TextArea("", 24, 80, TextArea.SCROLLBARS_VERTICAL_ONLY);
    add("Center", m_log);

    Panel pctl = new Panel();
    pctl.setLayout(new FlowLayout(FlowLayout.CENTER, 1, 1));
    add("South", pctl);

    Button bstart = new Button("Start");
    bstart.addActionListener(this);
    pctl.add(bstart);

    Button bstop = new Button("Stop");
    bstop.addActionListener(this);
    pctl.add(bstop);

    status = new Label("raw");
    status.setForeground(Color.green);
    status.setFont(new Font("SansSerif", Font.BOLD, 10));
    pctl.add(status);

    m_httpd = new httpd(m_port, m_docroot, this);
  }

  public void destroy() {
    stop();
  }

  public void paint(Graphics g) {
  }

  public void start() {
    m_httpd.start();
    status.setText("Running ");
    clear_log("Log started on " + new Date() + "\n");
  }

  public void stop() {
    m_httpd.stop();
    status.setText("Stopped ");
  }

  public void actionPerformed(ActionEvent ae) {
    String label = ae.getActionCommand();
    if (label.equals("Start")) {
      start();
    } else {
      stop();
    }
  }

  public void clear_log(String msg) {
    m_log.setText(msg + "\n");
  }

  public void log(String msg) {
    m_log.append(msg);
    status.setText(m_httpd.hits_served + " hits (" +
      (m_httpd.bytes_served / 1024) + "K), " +
      m_httpd.files_in_cache + " cached files (" +
      (m_httpd.bytes_in_cache / 1024) + "K), " +
      m_httpd.hits_to_cache + " cached hits");
    status.setSize(status.getPreferredSize());
  }
}
