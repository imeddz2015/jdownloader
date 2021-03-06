//jDownloader - Downloadmanager
//Copyright (C) 2009  JD-Team support@jdownloader.org
//
//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.plugins.hoster;

import java.io.IOException;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "xxxbunker.com" }, urls = { "http://(www\\.)?xxxbunkerdecrypted\\.com/[a-z0-9_\\-]+" }, flags = { 0 })
public class XxxBunkerCom extends PluginForHost {

    @SuppressWarnings("deprecation")
    public XxxBunkerCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    /* DEV NOTES */
    // Porn_plugin
    // Tags:
    // protocol: no https
    // other:

    /* Extension which will be used if no correct extension is found */
    private static final String  default_Extension = ".mp4";
    /* Connection stuff */
    private static final boolean free_resume       = true;
    private static final int     free_maxchunks    = 0;
    private static final int     free_maxdownloads = -1;

    private String               DLLINK            = null;

    @Override
    public String getAGBLink() {
        return "https://xxxbunker.com/tos.php";
    }

    @SuppressWarnings("deprecation")
    public void correctDownloadLink(final DownloadLink link) {
        link.setUrlDownload(link.getDownloadURL().replace("xxxbunkerdecrypted.com/", "xxxbunker.com/"));
    }

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws IOException, PluginException {
        DLLINK = null;
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        this.br.getHeaders().put("User-Agent", "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:38.0) Gecko/20100101 Firefox/38.0");
        br.getPage(downloadLink.getDownloadURL());
        if (br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        if (br.containsHTML(">FILE NOT FOUND<|>this video is no longer available")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } else if (br.getURL().equals("http://xxxbunker.com/")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } else if (br.containsHTML(">your video is being loaded, please wait")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } else if (br.containsHTML("<strong>SITE MAINTENANCE</strong>")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = br.getRegex("property=\"og:title\" content=\"([^<>\"]*?)\"").getMatch(0);
        if (filename == null) {
            filename = br.getRegex("class=vpVideoTitle><h1 itemprop=\"name\">([^<>\"]*?)</h1>").getMatch(0);
        }
        if (filename == null) {
            filename = new Regex(downloadLink.getDownloadURL(), "xxxbunker\\.com/(.+)").getMatch(0);
        }
        String externID_extern = br.getRegex("postbackurl(?:=|%3D)([^<>\"\\&]*?)%26amp%3B").getMatch(0);
        String externID = br.getRegex("player\\.swf\\?config=(http%3A%2F%2Fxxxbunker\\.com%2FplayerConfig\\.php%3F[^<>\"]*?)\"").getMatch(0);
        final String externID3 = br.getRegex("lvid=(\\d+)").getMatch(0);
        if (externID_extern == null && externID == null && externID3 == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (externID_extern != null) {
            /* E.g. http://xxxbunker.com/3568499 pornhub direct */
            externID_extern = Encoding.htmlDecode(externID_extern);
            externID_extern = Encoding.htmlDecode(externID_extern);
            DLLINK = Encoding.Base64Decode(externID_extern);
            // this.sleep(3000, downloadLink);
        } else if (externID != null) {
            br.getPage(Encoding.htmlDecode(externID));
            DLLINK = br.getRegex("<relayurl>([^<>\"]*?)</relayurl>").getMatch(0);
            externID = br.getRegex("<file>(http[^<>\"]*?)</file>").getMatch(0);
            if (DLLINK == null) {
                DLLINK = externID;
            }
        } else {
            br.getPage("http://xxxbunker.com/videoPlayer.php?videoid=" + externID3 + "&autoplay=true&ageconfirm=true&title=true&html5=false&hasflash=true&r=" + System.currentTimeMillis());
            DLLINK = br.getRegex("\\&amp;file=(http[^<>\"]*?\\.(?:flv|mp4))").getMatch(0);
        }
        if (DLLINK == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        DLLINK = Encoding.htmlDecode(DLLINK);
        filename = Encoding.htmlDecode(filename);
        filename = filename.trim();
        filename = encodeUnicode(filename);
        String ext = DLLINK.substring(DLLINK.lastIndexOf("."));
        /* Make sure that we get a correct extension */
        if (ext == null || !ext.matches("\\.[A-Za-z0-9]{3,5}")) {
            ext = default_Extension;
        }
        if (!filename.endsWith(ext)) {
            filename += ext;
        }
        downloadLink.setFinalFileName(filename);
        this.br = new Browser();
        br.getHeaders().put("Accept-Encoding", "identity");
        return AvailableStatus.TRUE;
        // // In case the link redirects to the finallink
        // br.setFollowRedirects(true);
        // URLConnectionAdapter con = null;
        // try {
        // try {
        // con = br.openHeadConnection(DLLINK);
        // /* Very important! Get the FINAL url! */
        // DLLINK = con.getRequest().getUrl();
        // } catch (final BrowserException e) {
        // throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        // }
        // if (!con.getContentType().contains("html")) {
        // downloadLink.setDownloadSize(con.getLongContentLength());
        // } else {
        // throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        // }
        // downloadLink.setProperty("directlink", DLLINK);
        // return AvailableStatus.TRUE;
        // } finally {
        // try {
        // con.disconnect();
        // } catch (final Throwable e) {
        // }
        // }
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, DLLINK, free_resume, free_maxchunks);
        if (dl.getConnection().getContentType().contains("html")) {
            if (dl.getConnection().getResponseCode() == 403) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 403", 60 * 60 * 1000l);
            } else if (dl.getConnection().getResponseCode() == 404) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 60 * 60 * 1000l);
            }
            br.followConnection();
            try {
                dl.getConnection().disconnect();
            } catch (final Throwable e) {
            }
            if (this.br.containsHTML(">SITE MAINTENANCE<")) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 'Site maintenance'", 5 * 60 * 1000l);
            }
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    /* Avoid chars which are not allowed in filenames under certain OS' */
    private static String encodeUnicode(final String input) {
        String output = input;
        output = output.replace(":", ";");
        output = output.replace("|", "¦");
        output = output.replace("<", "[");
        output = output.replace(">", "]");
        output = output.replace("/", "⁄");
        output = output.replace("\\", "");
        output = output.replace("*", "#");
        output = output.replace("?", "¿");
        output = output.replace("!", "¡");
        output = output.replace("\"", "'");
        return output;
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return free_maxdownloads;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetPluginGlobals() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }
}
