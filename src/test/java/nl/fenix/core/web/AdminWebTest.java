package nl.fenix.core.web;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AdminWebTest {

    @Test
    public void escapesPlayerTextAndStripsColorCodes() {
        // familienamen komen van spelers: nooit HTML doorlaten
        assertEquals("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;", AdminWeb.esc("<script>alert('x')</script>"));
        assertEquals("Dokker Lijn &amp; Co", AdminWeb.esc("&6Dokker §lLijn & Co"));
        assertEquals("", AdminWeb.esc(null));
    }
}
