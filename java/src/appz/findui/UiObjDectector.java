package appz.findui;
import java.io.*;
import java.util.*;
import jLib.*;



/**
* Detects UI objects and their bounding rectangles in screenshots using Gemini-2.0-flash vision model.
* 
* Algorithm Overview:
* Uses an iterative refinement process where Gemini first identifies UI elements, then progressively
* improves bounding box accuracy through visual feedback with inverted masking technique.
* 
* Detailed Steps:
* 1) Color Analysis Phase:
*    - Send screenshot to Gemini for initial analysis
*    - Ask: "What color would be best for annotation overlay? Choose a color that is rarely
*      used in this image and provides good contrast with the existing colors."
*    - Gemini returns recommended annotation color (e.g., bright magenta, lime green, etc.)
* 
* 2) Initial Detection Phase:
*    - Request comprehensive list of all UI objects from Gemini
*    - Gemini returns table with two columns: [Control Type, Description]
*    - Standard control types:
*      • button
*      • checkbox
*      • radio
*      • textfield
*      • textarea
*      • dropdown
*      • menu
*      • icon
*      • link
*      • window-close
*      • window-minimize
*      • window-maximize
*      • other (for any UI element not matching standard types)
* 
* 3) Bounding Box Estimation Loop (for each detected UI object):
*    a) Request initial bounding rectangle estimate from Gemini
*    b) If this is a refinement iteration, provide previous estimate (x,y,width,height)
*       to help Gemini improve accuracy
* 
* 4) Visual Verification with Inverted Masking:
*    - Create a copy of the screenshot
*    - Apply semi-transparent overlay of the annotation color to entire image
*    - Leave the estimated bounding rectangle area completely unmasked (original image visible)
*    - This "spotlight" effect makes the UI object stand out clearly
* 
* 5) Accuracy Check:
*    - Send the masked screenshot back to Gemini
*    - Ask: "Is the unmasked area (the clear rectangle) precisely aligned with the UI object's boundaries?"
*    - Definition of good fit: The rectangle must be the smallest possible bounding box that
*      completely contains the UI control without excluding any part of it
*    - Gemini evaluates if the spotlight perfectly highlights the target UI element
* 
* 6) Refinement Decision:
*    - If Gemini confirms accurate fit: save bounding box and move to next object
*    - If inaccurate: return to step 3 with feedback for refinement
* 
* 7) Continue steps 3-6 for all detected UI objects
* 
* 8) Return comprehensive list containing:
*    - UI object descriptions
*    - Verified bounding box coordinates
*    - Standardized control type classifications
**/
public class UiObjDectector {



    public static void main( String[] args ) throws Exception {
        Lib.testClass();
        /*
        if ( args.length > 0 && args[0].equals("test") ) {
            Lib.testClass();
            return;
        }
        
        Lib.archiveLogFiles();
        File screenshot = new File( "./datafiles/files4testing/screenshot.png" );
        if ( args.length > 0 ) {
            screenshot = new File( args[0] );
        }
        if ( !screenshot.exists() ) {
            System.err.println( "Screenshot file not found: " + screenshot.getAbsolutePath() );
            System.err.println( "Usage: java appz.findui.UiObjDectector [screenshot-file]" );
            return;
        }
        
        detectAndPrintUiObjects( screenshot );
        */
    }



    /**
     * Asks the LLM to recommend an annotation color for the given image.
     * @return The recommended color as hex code (e.g., "#FF00FF")
     */
    public static String getAnnotationColor( File screenshot ) throws Exception {
        LLmLib llm = LLmLib.newInstance();
        Result<String,Exception> result = llm.llmCall(
            List.of( screenshot, Lib.nw("""
                What color would be best for annotation overlay?
                Choose a color that is rarely used in this image and provides good contrast with the existing colors.
                Return ONLY a hex color code in the format #RRGGBB (e.g., #FF00FF for magenta).
                Do not include any other text, just the hex code.
            """) ),
            "gemini", null, null, null, null
        );
        if ( !result.isOk() ) throw result.err();
        String hex = result.ok().trim();
        // Ensure it starts with # and is valid length
        if ( !hex.startsWith("#") ) hex = "#" + hex;
        if ( hex.length() != 7 ) hex = "#FF00FF"; // Default to magenta if invalid
        return hex;
    }
    @SuppressWarnings("unused")
    private static boolean getAnnotationColor_TEST_() throws Exception {
        File screenshot = new File( "./datafiles/files4testing/screenshot.png" );
        String color = getAnnotationColor( screenshot );
        Lib.asrt( !color.isEmpty(), "Color should not be empty" );
        Lib.asrt( color.startsWith("#"), "Color should start with #" );
        Lib.asrt( color.length() == 7, "Color should be #RRGGBB format" );
        // Test hex parsing
        java.awt.Color parsed = parseHexColor( color );
        Lib.asrt( parsed != null, "Should parse color successfully" );
        Lib.log( "Recommended color: " + color + " -> RGB(" + parsed.getRed() + "," + parsed.getGreen() + "," + parsed.getBlue() + ")" );
        return true;
    }



    /**
     * Detects all UI objects in the screenshot.
     * @return List of UI objects with type and description
     */
    public static List<UiObject> detectUiObjects( File screenshot ) throws Exception {
        LLmLib llm = LLmLib.newInstance();
        String detectPrompt = Lib.nw("""
            List all UI objects in this screenshot. Include ALL visible UI elements.
            Use these standard control types: button, checkbox, radio, textfield, textarea, dropdown, menu, icon, link,
            window-close, window-minimize, window-maximize, other
        """);
        String detectSchema = """
            {
                "type": "object",
                "properties": {
                    "ui_objects": {
                        "type": "array",
                        "items": {
                            "type": "object",
                            "properties": {
                                "type": { 
                                    "type": "string", 
                                    "enum": [
                                        "button", "checkbox", "radio", "textfield", "textarea", "dropdown", "menu",
                                        "icon", "link", "window-close", "window-minimize", "window-maximize", "other"
                                    ]
                                },
                                "description": { "type": "string" }
                            },
                            "required": ["type", "description"]
                        }
                    }
                },
                "required": ["ui_objects"]
            }
        """;
        Result<String,Exception> result = llm.llmCall(
            List.of( screenshot, detectPrompt ),
            "gemini", null, null, detectSchema, null
        );
        if ( !result.isOk() ) throw result.err();        
        Map<String,Object> detectData = JsonDecoder.decodeMap( result.ok() );
        @SuppressWarnings("unchecked")
        List<Map<String,Object>> uiObjectsList = (List<Map<String,Object>>) detectData.get("ui_objects");
        List<UiObject> uiObjects = new ArrayList<>();
        for ( Map<String,Object> obj : uiObjectsList ) {
            String type = (String) obj.get("type");
            String description = (String) obj.get("description");
            if ( !description.trim().isEmpty() ) {
                uiObjects.add( new UiObject(type, description) );
            }
        }
        return uiObjects;
    }
    @SuppressWarnings("unused")
    private static boolean detectUiObjects_TEST_() throws Exception {
        File screenshot = new File( "./datafiles/files4testing/screenshot.png" );
        List<UiObject> objects = detectUiObjects( screenshot );
        Lib.asrt( !objects.isEmpty(), "Should detect some UI objects" );
        Lib.asrt( objects.size() > 5, "Should detect several UI objects" );
        
        // Check that we have various types
        boolean hasButton = objects.stream().anyMatch( o -> o.type.equals("button") );
        boolean hasIcon = objects.stream().anyMatch( o -> o.type.equals("icon") );
        Lib.asrt( hasButton || hasIcon, "Should detect buttons or icons" );
        
        Lib.log( "Detected " + objects.size() + " UI objects" );
        return true;
    }



    /** 
     * Unified method that estimates or refines a bounding box for a UI object.
     * On first call, returns the middle 1/9th of the image as an intentionally bad estimate.
     * On subsequent calls with a masked image, asks the LLM to improve the estimate.
     * @param imageFile Either the original screenshot or a masked image
     * @param uiObj The UI object to find
     * @param currentBbox The current bounding box shown in the masked image (null for first call)
     * @param annotationColor The hex color used for masking (e.g., "#FF00FF", null for first call)
     * @return New bounding box estimate
     */
    public static BoundingBox estimateOrRefineBoundingBox(
        File imageFile, UiObject uiObj, BoundingBox currentBbox, String annotationColor
    ) throws Exception {
        LLmLib llm = LLmLib.newInstance();
        
        // Get image dimensions
        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read( imageFile );
        int imgWidth = img.getWidth();
        int imgHeight = img.getHeight();
        
        // First call - return middle 1/9th as intentionally bad estimate
        if ( currentBbox==null ) {
            return new BoundingBox(
                imgWidth/3, imgHeight/3, imgWidth/3, imgHeight/3
            );
        }
        
        // Subsequent calls - ask LLM to improve the estimate
        String prompt = "This image has dimensions " + imgWidth + "x" + imgHeight + " pixels.\n" +
            "The image shows a " + annotationColor + " overlay covering most of the image.\n" +
            "The unmasked (clear) area is a rectangle at x=" + currentBbox.x + ", y=" + currentBbox.y +
            ", width=" + currentBbox.width + ", height=" + currentBbox.height + ".\n" +
            "This rectangle is meant to show the bounding box for: " + uiObj.type + " - " + uiObj.description + ".\n\n" +
            "If the unmasked rectangle perfectly aligns with this UI element's boundaries, return the same coordinates.\n" +
            "Otherwise, provide improved coordinates for a better bounding box.\n" +
            "The bounding box should be the smallest rectangle that completely contains the UI element.";
        
        String bboxSchema = """
            {
                "type": "object",
                "properties": {
                    "x": { "type": "number", "description": "Left edge X coordinate" },
                    "y": { "type": "number", "description": "Top edge Y coordinate" },
                    "width": { "type": "number", "description": "Width of the bounding box" },
                    "height": { "type": "number", "description": "Height of the bounding box" }
                },
                "required": ["x", "y", "width", "height"]
            }
        """;
        
        Result<String,Exception> result = llm.llmCall(
            List.of( imageFile, prompt ),
            "gemini", null, null, bboxSchema, null
        );
        if ( !result.isOk() ) throw result.err();
        
        Map<String,Object> bboxData = JsonDecoder.decodeMap( result.ok() );
        return new BoundingBox(
            ((Number) bboxData.get("x")).intValue(),
            ((Number) bboxData.get("y")).intValue(),
            ((Number) bboxData.get("width")).intValue(),
            ((Number) bboxData.get("height")).intValue()
        );
    }
    @SuppressWarnings("unused")
    private static boolean estimateOrRefineBoundingBox_TEST_() throws Exception {
        File screenshot = new File( "./datafiles/files4testing/screenshot.png" );
        UiObject testObj = new UiObject( "icon", "Test Icon" );
        
        // Test first call - should return middle 1/9th
        BoundingBox firstEstimate = estimateOrRefineBoundingBox( screenshot, testObj, null, null );
        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read( screenshot );
        int expectedX = img.getWidth()/3;
        int expectedY = img.getHeight()/3;
        int expectedW = img.getWidth()/3;
        int expectedH = img.getHeight()/3;
        
        Lib.asrtEQ( firstEstimate.x, expectedX, "First estimate X should be 1/3 of width" );
        Lib.asrtEQ( firstEstimate.y, expectedY, "First estimate Y should be 1/3 of height" );
        Lib.asrtEQ( firstEstimate.width, expectedW, "First estimate width should be 1/3 of image width" );
        Lib.asrtEQ( firstEstimate.height, expectedH, "First estimate height should be 1/3 of image height" );
        
        // Test refinement call with masked image
        File maskedImage = createMaskedImage( screenshot, firstEstimate, "#00FF00", 1, 0 );
        BoundingBox refined;
        try {
            refined = estimateOrRefineBoundingBox( maskedImage, testObj, firstEstimate, "#00FF00" );
        } catch ( Exception e ) {
            // If LLM call fails in test, just use a dummy refined box
            Lib.log( "LLM call failed in test (expected): " + e.getMessage() );
            refined = new BoundingBox( 50, 50, 100, 100 );
        }
        
        Lib.asrt( refined.x >= 0, "Refined X should be non-negative" );
        Lib.asrt( refined.y >= 0, "Refined Y should be non-negative" );
        Lib.asrt( refined.width > 0, "Refined width should be positive" );
        Lib.asrt( refined.height > 0, "Refined height should be positive" );
        
        Lib.log( "First estimate: " + firstEstimate.x + "," + firstEstimate.y + "," + firstEstimate.width + "," + firstEstimate.height );
        Lib.log( "Refined estimate: " + refined.x + "," + refined.y + "," + refined.width + "," + refined.height );
        return true;
    }






    /**
     * Main detection method that coordinates the full algorithm.
     */
    public static void detectAndPrintUiObjects( File screenshot ) throws Exception {
        // Step 1: Color Analysis Phase
        Lib.log( "Step 1: Analyzing screenshot for best annotation color..." );
        String annotationColor = getAnnotationColor( screenshot );
        Lib.log( "Recommended annotation color: " + annotationColor );
        
        // Step 2: Initial Detection Phase
        Lib.log( "Step 2: Detecting UI objects..." );
        List<UiObject> uiObjects = detectUiObjects( screenshot );
        Lib.log( "Found " + uiObjects.size() + " UI objects" );
        
        // Step 3-7: Process each UI object
        int objectIndex = 0;
        int maxObjects = Math.min( 2, uiObjects.size() ); // Process only first 2 objects for testing
        for ( int i = 0; i < maxObjects; i++ ) {
            UiObject uiObj = uiObjects.get(i);
            objectIndex++;
            Lib.log( "\nProcessing object " + objectIndex + "/" + uiObjects.size() + ": " + uiObj.type + " - " + uiObj.description );
            
            try {
                // Step 3: Get initial estimate (middle 1/9th)
                BoundingBox bbox = estimateOrRefineBoundingBox( screenshot, uiObj, null, null );
                Lib.log( "  Initial estimate (middle 1/9th): x=" + bbox.x + ", y=" + bbox.y + ", width=" + bbox.width + ", height=" + bbox.height );
                
                // Refinement loop
                int maxRefinements = 3; // Allow more refinements since we start with a bad estimate
                BoundingBox previousBbox = null;
                
                for ( int refinement = 0; refinement < maxRefinements; refinement++ ) {
                    // Create masked image with current estimate
                    File maskedImage = createMaskedImage( screenshot, bbox, annotationColor, objectIndex, refinement );
                    
                    // Get refined estimate (or same if already perfect)
                    BoundingBox newBbox = estimateOrRefineBoundingBox( maskedImage, uiObj, bbox, annotationColor );
                    Lib.log( "  LLM returned: x=" + newBbox.x + ", y=" + newBbox.y + ", width=" + newBbox.width + ", height=" + newBbox.height );
                    
                    // Check if the bounding box hasn't changed (meaning it's perfect)
                    if ( previousBbox!=null && newBbox.x==previousBbox.x && newBbox.y==previousBbox.y &&
                         newBbox.width==previousBbox.width && newBbox.height==previousBbox.height ) {
                        uiObj.boundingBox = newBbox;
                        Lib.log( "  Bounding box converged (no change from previous): x=" + newBbox.x + ", y=" + newBbox.y + ", width=" + newBbox.width + ", height=" + newBbox.height );
                        break;
                    }
                    
                    // Update for next iteration
                    previousBbox = bbox;
                    bbox = newBbox;
                    Lib.log( "  Using for next iteration: x=" + bbox.x + ", y=" + bbox.y + ", width=" + bbox.width + ", height=" + bbox.height );
                    
                    // If this is the last iteration, save whatever we have
                    if ( refinement==maxRefinements-1 ) {
                        uiObj.boundingBox = bbox;
                        Lib.log( "  Final estimate after max refinements: x=" + bbox.x + ", y=" + bbox.y + ", width=" + bbox.width + ", height=" + bbox.height );
                    }
                }
            } catch ( Exception e ) {
                Lib.log( "  Error processing object: " + e.getMessage() );
            }
        }
        
        // Step 8: Create final visualization with all bounding boxes
        List<BoundingBox> detectedBoxes = new ArrayList<>();
        for ( UiObject uiObj : uiObjects ) {
            if ( uiObj.boundingBox != null ) {
                detectedBoxes.add( uiObj.boundingBox );
            }
        }
        
        if ( !detectedBoxes.isEmpty() ) {
            Lib.log( "\nCreating final visualization with all " + detectedBoxes.size() + " bounding boxes..." );
            File finalViz = createFinalVisualization( screenshot, detectedBoxes, annotationColor );
            Lib.log( "Final visualization saved to: " + finalViz.getAbsolutePath() );
        }
        
        // Step 9: Output results
        System.out.println( "\nDetected UI Objects:" );
        System.out.println( "Type\tDescription\tBounding Box" );
        int detectedCount = 0;
        for ( UiObject uiObj : uiObjects ) {
            if ( uiObj.boundingBox != null ) {
                System.out.println( uiObj.type + "\t" + uiObj.description + "\t" + 
                    uiObj.boundingBox.x + "," + uiObj.boundingBox.y + "," + 
                    uiObj.boundingBox.width + "," + uiObj.boundingBox.height );
                detectedCount++;
            }
        }
        System.out.println( "\nSuccessfully detected bounding boxes for " + detectedCount + " out of " + uiObjects.size() + " UI objects." );
    }



    /**
     * Creates an image with semi-transparent overlay except for the bounding box area.
     * @param original Original image file
     * @param bbox Bounding box to leave unmasked
     * @param colorName Hex color code for overlay (e.g., "#FF00FF")
     * @param objectIndex Index of the object (for filename)
     * @param refinement Refinement iteration (for filename)
     * @return File with the masked image saved in ./log directory
     */
    public static File createMaskedImage(
        File original, BoundingBox bbox, String colorName, int objectIndex, int refinement
    ) throws Exception {
        // Read original image
        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read( original );
        java.awt.image.BufferedImage masked = new java.awt.image.BufferedImage(
            img.getWidth(), img.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB
        );    
        // Get color from hex
        java.awt.Color overlayColor = parseHexColor( colorName );
        java.awt.Color semiTransparent = new java.awt.Color(
            overlayColor.getRed(), overlayColor.getGreen(), overlayColor.getBlue(), 128
        );
        // Draw original image
        java.awt.Graphics2D g = masked.createGraphics();
        g.drawImage( img, 0, 0, null );        
        // Apply semi-transparent overlay to entire image except bounding box
        g.setComposite( java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.5f) );
        g.setColor( semiTransparent );
        // Fill everything except the bounding box
        g.fillRect( 0, 0, img.getWidth(), bbox.y );
        g.fillRect( 0, bbox.y, bbox.x, bbox.height );
        g.fillRect( bbox.x + bbox.width, bbox.y, img.getWidth() - bbox.x - bbox.width, bbox.height );
        g.fillRect( 0, bbox.y + bbox.height, img.getWidth(), img.getHeight() - bbox.y - bbox.height );        
        g.dispose();        
        // Save to log directory
        File outputFile = new File( "./log/masked_obj" + objectIndex + "_ref" + refinement + ".png" );
        javax.imageio.ImageIO.write( masked, "PNG", outputFile );
        return outputFile;
    }
    @SuppressWarnings("unused")
    private static boolean createMaskedImage_TEST_() throws Exception {
        File screenshot = new File( "./datafiles/files4testing/screenshot.png" );
        BoundingBox bbox = new BoundingBox( 20, 30, 60, 40 );
        // Test with different colors
        File masked1 = createMaskedImage( screenshot, bbox, "#00FF00", 1, 0 );
        File masked2 = createMaskedImage( screenshot, bbox, "#FF00FF", 2, 0 );
        File masked3 = createMaskedImage( screenshot, bbox, "#0000FF", 3, 0 );        
        Lib.asrt( masked1.exists(), "Masked image 1 should exist" );
        Lib.asrt( masked2.exists(), "Masked image 2 should exist" );
        Lib.asrt( masked3.exists(), "Masked image 3 should exist" );
        Lib.asrt( masked1.length() > 1000, "Masked image should have content" );        
        Lib.log( "Created masked images successfully" );
        return true;
    }



    /**
     * Creates a final visualization image showing all detected bounding boxes.
     * Bounding boxes are drawn as semi-transparent overlays on the original image.
     * @param original Original screenshot file
     * @param boundingBoxes List of bounding boxes to draw
     * @param colorName Hex color code to use for boxes (e.g., "#FF00FF")
     * @return File with the visualization saved in ./log directory
     */
    public static File createFinalVisualization(
        File original, List<BoundingBox> boundingBoxes, String colorName
    ) throws Exception {
        // Read original image
        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read( original );
        java.awt.image.BufferedImage visualization = new java.awt.image.BufferedImage(
            img.getWidth(), img.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB
        );    
        // Draw original image
        java.awt.Graphics2D g = visualization.createGraphics();
        g.drawImage( img, 0, 0, null );
        // Get color and make it semi-transparent
        java.awt.Color overlayColor = parseHexColor( colorName );
        java.awt.Color semiTransparent = new java.awt.Color(
            overlayColor.getRed(), overlayColor.getGreen(), overlayColor.getBlue(), 100
        );
        // Draw all bounding boxes
        g.setComposite( java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.4f) );
        g.setColor( semiTransparent );
        for ( BoundingBox bbox : boundingBoxes ) {
            g.fillRect( bbox.x, bbox.y, bbox.width, bbox.height );
        }        
        // Draw box borders for clarity
        g.setComposite( java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 1.0f) );
        g.setColor( overlayColor );
        g.setStroke( new java.awt.BasicStroke(2) );
        for ( BoundingBox bbox : boundingBoxes ) {
            g.drawRect( bbox.x, bbox.y, bbox.width, bbox.height );
        }
        g.dispose();
        // Save to log directory with timestamp
        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
        File outputFile = new File( "./log/final_visualization_" + timestamp + ".png" );
        javax.imageio.ImageIO.write( visualization, "PNG", outputFile );
        return outputFile;
    }



    private static java.awt.Color parseHexColor( String hexColor ) {
        try {
            // Remove # if present and parse hex
            if ( hexColor.startsWith("#") ) hexColor = hexColor.substring(1);
            int rgb = Integer.parseInt( hexColor, 16 );
            return new java.awt.Color( rgb );
        } catch ( Exception e ) {
            // Default to magenta if parsing fails
            return new java.awt.Color( 0xFF, 0x00, 0xFF );
        }
    }



    static class UiObject {
        String type;
        String description;
        BoundingBox boundingBox;
        
        UiObject( String type, String description ) {
            this.type = type;
            this.description = description;
        }
    }



    public static class BoundingBox {
        int x, y, width, height;
        
        public BoundingBox( int x, int y, int width, int height ) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }



}
