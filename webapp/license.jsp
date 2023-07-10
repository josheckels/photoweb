<%@ page import="com.stampysoft.photoGallery.servlet.PhotoDetail" %>
<%@ page import="java.util.List" %>
<p>&nbsp;</p>

<p style="text-align: center; font-size: small">
    <% if (request.getAttribute("Photographer") != null)
    { %>
    <%= ((com.stampysoft.photoGallery.Photographer) request.getAttribute("Photographer")).getCopyright() %>
    <% } %>
</p>

<!-- Google tag (gtag.js) -->
<script async src="https://www.googletagmanager.com/gtag/js?id=G-7NSX5M7D0E"></script>
<script>
    window.dataLayer = window.dataLayer || [];
    function gtag(){dataLayer.push(arguments);}
    gtag('js', new Date());

    gtag('config', 'G-7NSX5M7D0E');
</script>