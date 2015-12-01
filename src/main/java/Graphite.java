/*
 * (C) Copyright 2015 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Benoit Delbosc
 */

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;


public class Graphite {
    private final static Logger log = Logger.getLogger(Report.class);
    String dashboardUrl, user, password;
    String baseUrl;
    List<Image> images = new ArrayList<>();
    String from, until;
    File outputDirectory;


    class Image {
        String url;
        String title;
        String filename;

        public Image(String url, String title, String from, String until) {
            this.url = getUrl(url, from, until);
            this.title = title;
        }

        private String getUrl(String url, String from, String until) {
            return url + String.format("&from=%s&until=%s", from, until);
        }

        public File getFile(File outputDirectory) {
            filename = (title + "_" + from).replaceAll("\\W+", "") + ".png";
            return new File(outputDirectory, filename);
        }
    }

    public Graphite(String graphiteUrl, String user, String password, SimulationContext stats, File outputDirectory) {
        this.dashboardUrl = graphiteUrl;
        baseUrl = Utils.getBaseUrl(graphiteUrl);
        this.from = getDateAsString(stats.simStat.start);
        this.until = getDateAsString(stats.simStat.end + 60000L); // add one more minute to prevent empty chart
        this.outputDirectory = outputDirectory;
        this.user = user;
        this.password = password;
        Utils.setBasicAuth(user, password);
        parseDashboard();
        downloadImages();
    }

    private String getDateAsString(long start) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm_yyyyMMdd").withZone(ZoneId.systemDefault());
        return formatter.format(Instant.ofEpochMilli(start));
    }

    private void downloadImages() {
        images.forEach(image -> {
            try {
                downloadImage(image);
            } catch (IOException e) {
                log.warn("Fail to download image: " + image.title);
                if (log.isDebugEnabled())
                    log.debug("Download error", e);
            }
        });
    }

    private void downloadImage(Image image) throws IOException {
        File dest = image.getFile(outputDirectory);
        Utils.download(new URL(image.url), dest);
    }

    private void parseDashboard() {
        JSONParser parser = new JSONParser();
        Object obj;
        try {
            obj = parser.parse(getJsonDashboard());
        } catch (ParseException | IOException e) {
            log.error("Failed to parse Graphite dashboard: " + dashboardUrl, e);
            throw new IllegalArgumentException("invalid dashboard link " + dashboardUrl, e);
        }
        JSONObject jsonObject = (JSONObject) obj;
        JSONArray graphs = (JSONArray) ((JSONObject) jsonObject.get("state")).get("graphs");
        for (Object graph : graphs) {
            String title = ((JSONObject) ((JSONArray) graph).get(1)).get("title").toString();
            String graphUrl = ((String) ((JSONArray) graph).get(2));
            images.add(new Image(baseUrl + graphUrl, title, from, until));
        }
    }

    private String getJsonDashboard() throws IOException {
        String url = getJsonDashboardUrl();
        log.info("Downloading: " + url);
        return Utils.getContent(new URL(url));
    }

    private String getJsonDashboardUrl() {
        return String.format("%s?from=%s&until=%s", dashboardUrl.replace("/dashboard/#", "/dashboard/load/"),
                from, until);
    }


}