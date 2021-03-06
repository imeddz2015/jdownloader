//jDownloader - Downloadmanager
//Copyright (C) 2010  JD-Team support@jdownloader.org
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

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import jd.PluginWrapper;
import jd.config.Property;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import org.jdownloader.captcha.v2.challenge.recaptcha.v1.Recaptcha;
import jd.plugins.components.SiteType.SiteTemplate;

import org.appwork.utils.formatter.SizeFormatter;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "zalohuj.to" }, urls = { "https?://(www\\.)?zalohuj\\.to/[A-Za-z0-9]+" }, flags = { 0 })
public class ZalohujTo extends PluginForHost {

    public ZalohujTo(PluginWrapper wrapper) {
        super(wrapper);
        // this.enablePremium(MAINPAGE + "/upgrade." + TYPE);
    }

    // For sites which use this script: http://www.yetishare.com/
    // YetiShareBasic Version 0.3.6-psp
    // mods:
    // limit-info:
    // protocol: no https
    // captchatype: null
    // other:

    @Override
    public String getAGBLink() {
        return mainpage + "/terms." + type;
    }

    /* Basic constants */
    private final String         mainpage                                     = "http://zalohuj.to";
    private final String         domains                                      = "(zalohuj\\.to)";
    private final String         type                                         = "html";
    private static final int     wait_BETWEEN_DOWNLOADS_LIMIT_MINUTES_DEFAULT = 10;
    private static final int     additional_WAIT_SECONDS                      = 3;
    private static final boolean supportshttps                                = true;
    private static final boolean supportshttps_FORCED                         = true;
    /* In case there is no information when accessing the main link */
    private static final boolean available_CHECK_OVER_INFO_PAGE               = false;
    /* Known errors */
    private static final String  url_ERROR_SIMULTANDLSLIMIT                   = "e=You+have+reached+the+maximum+concurrent+downloads";
    private static final String  url_ERROR_SERVER                             = "e=Error%3A+Could+not+open+file+for+reading.";
    private static final String  url_ERROR_WAIT_BETWEEN_DOWNLOADS_LIMIT       = "e=You+must+wait+";
    private static final String  url_ERROR_PREMIUMONLY                        = "e=You+must+register+for+a+premium+account+to+download+files+of+this+size";
    /* Texts for the known errors */
    private static final String  errortext_ERROR_WAIT_BETWEEN_DOWNLOADS_LIMIT = "You must wait between downloads!";
    private static final String  errortext_ERROR_SERVER                       = "Server error";
    private static final String  errortext_ERROR_PREMIUMONLY                  = "This file can only be downloaded by premium (or registered) users";
    private static final String  errortext_ERROR_SIMULTANDLSLIMIT             = "Max. simultan downloads limit reached, wait to start more downloads from this host";

    /* Connection stuff */
    private static final boolean free_RESUME                                  = true;
    private static final int     free_MAXCHUNKS                               = 0;
    private static final int     free_MAXDOWNLOADS                            = 20;
    private static final boolean account_FREE_RESUME                          = true;
    private static final int     account_FREE_MAXCHUNKS                       = 0;
    private static final int     account_FREE_MAXDOWNLOADS                    = 20;
    private static final boolean account_PREMIUM_RESUME                       = true;
    private static final int     account_PREMIUM_MAXCHUNKS                    = 0;
    private static final int     account_PREMIUM_MAXDOWNLOADS                 = 20;

    private static AtomicInteger MAXPREM                                      = new AtomicInteger(1);

    @Override
    public void correctDownloadLink(final DownloadLink link) {
        /* link cleanup, but respect users protocol choosing or forced protocol */
        if (!supportshttps) {
            link.setUrlDownload(link.getDownloadURL().replaceFirst("https://", "http://"));
        } else if (supportshttps && supportshttps_FORCED) {
            link.setUrlDownload(link.getDownloadURL().replaceFirst("http://", "https://"));
        }
    }

    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        String filename;
        String filesize;
        if (available_CHECK_OVER_INFO_PAGE) {
            br.getPage(link.getDownloadURL() + "~i");
            if (!br.getURL().contains("~i")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            filename = br.getRegex("Filename:[\t\n\r ]+</td>[\t\n\r ]+<td>([^<>\"]*?)<").getMatch(0);
            if (filename == null || inValidate(Encoding.htmlDecode(filename).trim()) || Encoding.htmlDecode(filename).trim().equals("  ")) {
                /* Filename might not be available here either */
                filename = new Regex(link.getDownloadURL(), "([a-z0-9]+)$").getMatch(0);
            }
            filesize = br.getRegex("Filesize:[\t\n\r ]+</td>[\t\n\r ]+<td>([^<>\"]*?)</td>").getMatch(0);
        } else {
            br.getPage(link.getDownloadURL());
            handleErrors();
            if (br.getURL().contains(url_ERROR_WAIT_BETWEEN_DOWNLOADS_LIMIT)) {
                link.setName(getFID(link));
                link.getLinkStatus().setStatusText(errortext_ERROR_WAIT_BETWEEN_DOWNLOADS_LIMIT);
                return AvailableStatus.TRUE;
            } else if (br.getURL().contains(url_ERROR_SERVER)) {
                link.setName(getFID(link));
                link.getLinkStatus().setStatusText(errortext_ERROR_SERVER);
                return AvailableStatus.TRUE;
            } else if (br.getURL().contains(url_ERROR_PREMIUMONLY)) {
                link.getLinkStatus().setStatusText(errortext_ERROR_PREMIUMONLY);
                return AvailableStatus.TRUE;
            }
            if (br.getURL().contains("/error." + type) || br.getURL().contains("/index." + type) || (!br.containsHTML("class=\"downloadPageTable(V2)?\"") && !br.containsHTML("class=\"download\\-timer\""))) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            final Regex fInfo = br.getRegex("<strong>([^<>\"]*?) \\((\\d+(,\\d+)?(\\.\\d+)? (KB|MB|GB))\\)<");
            filename = fInfo.getMatch(0);
            filesize = fInfo.getMatch(1);
            if (filename == null || filesize == null) {
                /* Get piece of the page which usually contains filename- and size */
                final String page_piece = br.getRegex("(<div class=\"contentPageWrapper\">.*?class=\"link btn\\-free\")").getMatch(0);
                if (page_piece != null) {
                    final String endings = jd.plugins.hoster.DirectHTTP.ENDINGS;
                    if (filename == null) {
                        filename = new Regex(page_piece, "([^<>/\r\n\t:\\?\"]+" + endings + "[^<>/\r\n\t:\\?\"]*)").getMatch(0);
                    }
                    if (filesize == null) {
                        filesize = new Regex(page_piece, "(\\d+(,\\d+)?(\\.\\d+)? (KB|MB|GB))").getMatch(0);
                    }
                }
            }
        }
        if (filename == null || filesize == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        link.setName(Encoding.htmlDecode(filename).trim());
        link.setDownloadSize(SizeFormatter.getSize(Encoding.htmlDecode(filesize.replace(",", "")).trim()));
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        if (available_CHECK_OVER_INFO_PAGE) {
            br.getPage(downloadLink.getDownloadURL());
        }
        doFree(downloadLink, "free_directlink", free_RESUME, free_MAXCHUNKS);
    }

    public void doFree(final DownloadLink downloadLink, final String directlinkproperty, final boolean resume, final int maxchunks) throws Exception, PluginException {
        boolean captcha = false;
        String continue_link = checkDirectLink(downloadLink, directlinkproperty);
        if (continue_link != null) {
            dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, continue_link, resume, maxchunks);
        } else {
            if (br.getURL().contains(url_ERROR_SIMULTANDLSLIMIT)) {
                throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, errortext_ERROR_SIMULTANDLSLIMIT, 1 * 60 * 1000l);
            } else if (br.getURL().contains(url_ERROR_WAIT_BETWEEN_DOWNLOADS_LIMIT)) {
                final String wait_minutes = new Regex(br.getURL(), "wait\\+(\\d+)\\+minutes?").getMatch(0);
                if (wait_minutes != null) {
                    throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, errortext_ERROR_WAIT_BETWEEN_DOWNLOADS_LIMIT, Integer.parseInt(wait_minutes) * 60 * 1001l);
                }
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, errortext_ERROR_WAIT_BETWEEN_DOWNLOADS_LIMIT, wait_BETWEEN_DOWNLOADS_LIMIT_MINUTES_DEFAULT * 60 * 1001l);
            } else if (br.getURL().contains(url_ERROR_SERVER)) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, errortext_ERROR_SERVER, 5 * 60 * 1000l);
            } else if (br.getURL().contains(url_ERROR_PREMIUMONLY)) {
                try {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
                } catch (final Throwable e) {
                    if (e instanceof PluginException) {
                        throw (PluginException) e;
                    }
                }
                throw new PluginException(LinkStatus.ERROR_FATAL, errortext_ERROR_PREMIUMONLY);
            }

            /* Handle up to 3 pre-download pages before the (eventually existing) captcha */
            for (int i = 1; i <= 3; i++) {
                logger.info("Handling pre-download page #" + i);
                continue_link = br.getRegex("\\$\\(\\'\\.download\\-timer\\'\\)\\.html\\(\"<a href=\\'(https?://[^<>\"]*?)\\'").getMatch(0);
                if (continue_link == null) {
                    continue_link = br.getRegex("\"(https?://" + domains + "/[A-Za-z0-9]+\\?download_token=[A-Za-z0-9]+)\"").getMatch(0);
                }
                if (continue_link == null && i == 0) {
                    continue_link = downloadLink.getDownloadURL() + "?d=1";
                    logger.info("Could not find continue_link --> Using standard continue_link, continuing...");
                } else if (continue_link == null && i > 0) {
                    logger.info("No continue_link available, stepping out of pre-download loop");
                    break;
                } else {
                    logger.info("Found continue_link, continuing...");
                }
                final String waittime = br.getRegex("\\$\\(\\'\\.download\\-timer\\-seconds\\'\\)\\.html\\((\\d+)\\);").getMatch(0);
                if (waittime != null) {
                    logger.info("Found waittime, waiting (seconds): " + waittime + " + " + additional_WAIT_SECONDS + " additional seconds");
                    sleep((Integer.parseInt(waittime) + additional_WAIT_SECONDS) * 1001l, downloadLink);
                } else {
                    logger.info("Current pre-download page has no waittime");
                }
                dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, continue_link, resume, maxchunks);
                if (dl.getConnection().isContentDisposition()) {
                    break;
                }
                br.followConnection();
                if (br.getURL().contains(url_ERROR_SERVER)) {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, errortext_ERROR_SERVER, 5 * 60 * 1000l);
                }
            }
        }
        if (!dl.getConnection().isContentDisposition()) {
            /* Do not follow connection, already done above */
            handleErrors();
            final String captchaAction = br.getRegex("<div class=\"captchaPageTable\">[\t\n\r ]+<form method=\"POST\" action=\"(https?://[^<>\"]*?)\"").getMatch(0);
            final String rcID = br.getRegex("recaptcha/api/noscript\\?k=([^<>\"]*?)\"").getMatch(0);
            if (captchaAction == null || rcID == null) {
                logger.warning("Failed to find captcha information");
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            captcha = true;
            final Recaptcha rc = new Recaptcha(br, this);
            rc.setId(rcID);
            rc.load();
            for (int icaptcha = 1; icaptcha <= 5; icaptcha++) {
                File cf = rc.downloadCaptcha(getLocalCaptchaFile());
                String c = getCaptchaCode("recaptcha", cf, downloadLink);
                dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, captchaAction, "submit=continue&submitted=1&d=1&recaptcha_challenge_field=" + rc.getChallenge() + "&recaptcha_response_field=" + c, resume, maxchunks);
                if (!dl.getConnection().isContentDisposition()) {
                    br.followConnection();
                    if (br.getURL().contains("error.php?e=Error%3A+Could+not+open+file+for+reading")) {
                        throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Server error", 60 * 60 * 1000l);
                    }
                    rc.reload();
                    continue;
                }
                break;
            }
        }
        if (!dl.getConnection().isContentDisposition()) {
            br.followConnection();
            if (captcha && br.containsHTML("(api\\.recaptcha\\.net|google\\.com/recaptcha/api/)")) {
                throw new PluginException(LinkStatus.ERROR_CAPTCHA);
            }
            handleErrors();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        /* Directlink is only available if we have no captcha */
        if (!captcha) {
            downloadLink.setProperty(directlinkproperty, continue_link);
        }
        dl.startDownload();
    }

    private void handleErrors() throws PluginException {
        if (br.containsHTML("Error: Too many concurrent download requests")) {
            throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Wait before starting new downloads", 3 * 60 * 1000l);
        } else if (br.getURL().contains(url_ERROR_SIMULTANDLSLIMIT)) {
            throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, errortext_ERROR_SIMULTANDLSLIMIT, 1 * 60 * 1000l);
        }
    }

    private String checkDirectLink(final DownloadLink downloadLink, final String property) {
        String dllink = downloadLink.getStringProperty(property);
        if (dllink != null) {
            try {
                final Browser br2 = br.cloneBrowser();
                br2.setFollowRedirects(true);
                URLConnectionAdapter con = br2.openGetConnection(dllink);
                if (con.getContentType().contains("html") || con.getLongContentLength() == -1) {
                    downloadLink.setProperty(property, Property.NULL);
                    dllink = null;
                }
                con.disconnect();
            } catch (final Exception e) {
                downloadLink.setProperty(property, Property.NULL);
                dllink = null;
            }
        }
        return dllink;
    }

    private String getFID(final DownloadLink dl) {
        return new Regex(dl.getDownloadURL(), "([A-Za-z0-9]+)$").getMatch(0);
    }

    /**
     * Validates string to series of conditions, null, whitespace, or "". This saves effort factor within if/for/while statements
     *
     * @param s
     *            Imported String to match against.
     * @return <b>true</b> on valid rule match. <b>false</b> on invalid rule match.
     * @author raztoki
     * */
    private boolean inValidate(final String s) {
        if (s == null || s != null && (s.matches("[\r\n\t ]+") || s.equals(""))) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return free_MAXDOWNLOADS;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    @Override
    public SiteTemplate siteTemplateType() {
        return SiteTemplate.MFScripts_YetiShare;
    }

}