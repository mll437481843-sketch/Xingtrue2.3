import javax.imageio.*;
import javax.imageio.stream.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class Scale {
    public static void main(String[] a) throws Exception {
        BufferedImage src = ImageIO.read(new File(a[0]));
        int w = Integer.parseInt(a[2]);
        int h = (int) Math.round(src.getHeight() * (double) w / src.getWidth());
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        ImageWriter iw = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam p = iw.getDefaultWriteParam();
        p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        p.setCompressionQuality(Float.parseFloat(a[3]));
        ImageOutputStream ios = ImageIO.createImageOutputStream(new File(a[1]));
        iw.setOutput(ios);
        iw.write(null, new IIOImage(out, null, null), p);
        ios.close();
        System.out.println(a[1] + " " + w + "x" + h);
    }
}
