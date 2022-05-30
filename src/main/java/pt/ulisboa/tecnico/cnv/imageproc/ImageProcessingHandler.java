package pt.ulisboa.tecnico.cnv.imageproc;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public abstract class ImageProcessingHandler implements HttpHandler, RequestHandler<Map<String,String>, String> {

    abstract BufferedImage process(BufferedImage bi) throws IOException;

    private String handleRequest(String inputEncoded, String format) {
        byte[] decoded = Base64.getDecoder().decode(inputEncoded);
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(decoded);
            BufferedImage bi = ImageIO.read(bais);
            bi = process(bi);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bi, format, baos);
            byte[] outputEncoded = Base64.getEncoder().encode(baos.toByteArray());
            return new String(outputEncoded);
        } catch (IOException e) {
            return e.toString();
        }
    }

    @Override
    public void handle(HttpExchange t) throws IOException {
        if (t.getRequestHeaders().getFirst("Origin") != null) {
            t.getResponseHeaders().add("Access-Control-Allow-Origin", t.getRequestHeaders().getFirst("Origin"));
        }
        if (t.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            t.getResponseHeaders().add("Access-Control-Allow-Methods", "POST");
            t.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,API-Key");
            t.sendResponseHeaders(204, -1);
        } else {
            InputStream stream = t.getRequestBody();
            // Result syntax: data:image/<format>;base64,<encoded image>
            String result = new BufferedReader(new InputStreamReader(stream)).lines().collect(Collectors.joining("\n"));
            String[] resultSplits = result.split(",");
            String format = resultSplits[0].split("/")[1].split(";")[0];
            String output = handleRequest(resultSplits[1], format);
            t.sendResponseHeaders(200, output.length());
            OutputStream os = t.getResponseBody();
            os.write(output.getBytes());
            os.close();
        }
    }

    @Override
    public String handleRequest(Map<String,String> event, Context context) {
        return handleRequest(event.get("body"), event.get("fileFormat"));
    }
}
