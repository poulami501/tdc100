java -Dtdc.home="%1\dist" -Djetty.home="%1\dist\servletcontainer\jetty-5.1.11RC0" -Dorg.mortbay.log.LogFactory.noDiscovery=false -Djetty.class.path="%1\dist\servletcontainer\jetty-5.1.11RC0\etc" -cp "%1\dist\servletcontainer\jetty-5.1.11RC0\lib\javax.servlet.jar;%1\dist\etc;%1\dist\servletcontainer\jetty-5.1.11RC0\lib\org.mortbay.jetty.jar" -jar "%1\dist\servletcontainer\jetty-5.1.11RC0\stop.jar" 
