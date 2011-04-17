import java.awt.*;
import java.awt.image.ColorModel;
import java.io.*;
import java.util.*;
import ij.*;
import ij.io.*;
import ij.gui.*;
import ij.measure.*;
import ij.plugin.*;
import ij.process.*;


/** This plugin reads ICS files. 
	It should work with both version 1.0 and 2.0.
	However, it has not been extensively tested, and is likely to fail on
	some ics files.  If so, please send me a (small) examples

	Author: Nico Stuurman <nico@cmp.ucsf.edu>
	Modified by Wayne Rashband (wsr)
	Modified by Thomas Miedema <thomasmiedema@gmail.com>
*/
public class Ics_Opener_To_Tiff extends Opener implements PlugIn {

	private static String defaultDirectory = null;
   boolean interlaced = false;
   // Maximmum number of channels displayed in individual stacks
   static int MAX_CHANNELS=5;

   public void run(String arg) {
      if (arg.equals("about")) {
         showAbout(); 
      } else if (arg.equals("open")) {
         this.openMultiple();
      } else {
         this.openMultiple();
      }
      return; 
   }        

   private void showAbout() {
      IJ.showMessage("Ics_Opener_To_Tiff", "This plugins opens Ics/Ids files and converts channel 3 to Tiff \n" +
            "   Nico Stuurman, January 2007\n" + 
				"   Thomas Miedema (thomasmiedema@gmail.com), March 2011"
            );
   }

   
	public void open(String path) {
		if (!IJ.isJava2()) { //wsr
			IJ.showMessage("ICS Opener", "This plugin requires Java 1.2 or later.");
			return;
		}
		File file = new File(path);

		String directory = file.getParent();
		String fileName = file.getName();

		String icsfileName;
		String idsfileName;
		if (fileName==null)
			return;

		// sort out the ics and ids filenames
		if (fileName.endsWith(".ics")) {
			icsfileName=new String (fileName);
			idsfileName=fileName.substring(0,fileName.length()-4)+".ids";
		}
      else if (fileName.endsWith(".ICS")) {
			icsfileName=new String (fileName);
			idsfileName=fileName.substring(0,fileName.length()-4)+".IDS";
		}
		else if (fileName.endsWith(".ids")) {
				idsfileName=new String (fileName);
				icsfileName=fileName.substring(0,fileName.length()-4)+".ics";
				File f = new File(directory, icsfileName); 
				if (!f.exists()) {
					IJ.showMessage("ICS Opener", "\""+icsfileName+"\" not found");
					return;
				}
		}
		else if (fileName.endsWith(".IDS")) {
				idsfileName=new String (fileName);
				icsfileName=fileName.substring(0,fileName.length()-4)+".ICS";
				File f = new File(directory, icsfileName); 
				if (!f.exists()) {
					IJ.showMessage("ICS Opener", "\""+icsfileName+"\" not found");
					return;
				}
		}
		else {
			IJ.showMessage ("ICS Opener", "Please select a file with the .ics or .ids suffix."); //wsr
			return;
		}
		IcsImport d = new IcsImport(icsfileName, idsfileName, directory);
		d.OpenIcs();
	}
	
	public class IcsImport {
		private String icsFileName;
		private String idsFileName;
		private String directory;
		private Calibration cal;

		public IcsImport (String icsFileName, String idsFileName, String directory) {
			this.icsFileName=icsFileName;
			this.idsFileName=idsFileName;
			this.directory=directory;
		
		}

		public FileInfo[] IcsInfo () {
			File icsFile=new File(this.directory, this.icsFileName);
			String line;
			Integer numpars=new Integer(0);
			String[] paramkeys=new String[0];
			Integer[] paramvalues=new Integer[0];
			Float[] scalevalues=new Float[0];
			String[] scalekeys=new String[0];
			boolean scalekeysFound=false;
			String format="";
			String sign="";
			String compression="";
			Integer[] byte_order;
			boolean intelByteOrder=false;
			Float ics_version=new Float (0);
			// ints go up to 2GB, should be enough for now...
			Long offset=new Long(0);
			int bits=0,x=0,y=0,z=1,t=1,ch=1;
			int bitsOffset=0,xOffset=0,yOffset=0,zOffset=0,tOffset=0,chOffset=0;
			
			if (!icsFile.canRead())
				return null;
			try {
				RandomAccessFile input=new RandomAccessFile(icsFile,"r");
				while ( (line=input.readLine()) != null && !(line.startsWith("end"))) {
					// IJ.write (line);
					// and now re-write String.split();
					StringTokenizer tmptok=new StringTokenizer(line);
					String [] tmp=new String[tmptok.countTokens()];
					int i=0;
					while (tmptok.hasMoreTokens()) {
						tmp[i]=tmptok.nextToken();
						i++;
					}
					if (i>=2) {
						if (tmp[0].equals("ics_version")) 
							ics_version=new Float(tmp[1]);
						// ics 2.0 source file def
						if (tmp[0].equals("source")) {
							if (tmp[1].equals("file")) {
								this.idsFileName=tmp[2];
							}
						}
						// ics 2.0 offset def
						if (tmp[0].equals("source")) {
							if (tmp[1].equals("offset")) {
								offset=new Long(tmp[2]);
							}
						}
						
						if (tmp[0].equals("layout")) {
							if (tmp[1].equals("parameters")) {
								numpars=new Integer(tmp[2]);
							}
							if (tmp[1].equals("order")) {
								paramkeys=new String[numpars.intValue()];
								int j=0;
								for (j=0;j<numpars.intValue();j++) {
									paramkeys[j]=tmp[j+2];
								}
                        if (paramkeys[1].equals("ch") || paramkeys[1].equals("channels")) {
                           interlaced = true;
                        }
							}
							if (tmp[1].equals("sizes")) {
								paramvalues=new Integer[numpars.intValue()];
								int j=0;
								for (j=0;j<numpars.intValue();j++) {
									paramvalues[j]=new Integer(tmp[j+2]);
								}
							}
						}
						if (tmp[0].equals("representation")) {
							if(tmp[1].equals("format"))
								format=new String(tmp[2]);	
							if(tmp[1].equals("sign"))
								sign=new String(tmp[2]);	
							if(tmp[1].equals("compression"))
								compression=new String(tmp[2]);	
							if(tmp[1].equals("byte_order")) {
								int nr_bytes=paramvalues[0].intValue()/8;
								byte_order=new Integer[nr_bytes];	
								for (int bo=0; bo<(nr_bytes); bo++) 
									byte_order[bo]=new Integer(tmp[2 + bo]);	
								if (nr_bytes>1) {
                           // This is counter intuitive to me, but seems to work alright:
									if (byte_order[1].intValue()>byte_order[0].intValue())
										intelByteOrder=true;
									else
										intelByteOrder=false;
								}
								else
									intelByteOrder=false;	
							}
						}
						if (tmp[0].equals("parameter")) {
							if(tmp[1].equals("scale")) {
								scalevalues=new Float[numpars.intValue()];
								int j=0;
								for (j=0;j<numpars.intValue();j++) {
									scalevalues[j]=new Float(tmp[j+2]);
								}
							}
							if(tmp[1].equals("units")) {
								scalekeys=new String[numpars.intValue()];
								int j=0;
								for (j=0;j<numpars.intValue();j++) {
									scalekeys[j]=new String(tmp[j+2]);
								}
								scalekeysFound=true;
							}
						}
					}
				}
				if (line!=null && line.startsWith("end") && (ics_version.compareTo(new Float(1.0)) > 0) ) { //wsr
					offset=new Long(input.getFilePointer());
				}
			}
			catch (FileNotFoundException fnf) {
				IJ.log("File: "+this.icsFileName+" not found.");
			}
			catch (Exception e) {
				CharArrayWriter caw = new CharArrayWriter(); //wsr
				PrintWriter pw = new PrintWriter(caw);
				e.printStackTrace(pw);
				IJ.log(caw.toString());
			}
			// IJ.write ("ICS: "+ this.icsFileName+", IDS: "+this.idsFileName+", directory: "+this.directory);
			 //IJ.write ("Numpars: "+numpars.toString());
			for (int j=0;j<numpars.intValue();j++) {
				  //IJ.write("Key: "+paramkeys[j]);
				  //IJ.write("Value: "+paramvalues[j]);
				if (paramkeys[j].equals("bits")) {
					bitsOffset=j;
					bits=paramvalues[j].intValue();
				}
				if (paramkeys[j].equals("x")) {
					xOffset=j;
					x=paramvalues[j].intValue();
				}
				if (paramkeys[j].equals("y")) {
					yOffset=j;
					y=paramvalues[j].intValue();
				}
				if (paramkeys[j].equals("t")) {
					tOffset=j;
					t=paramvalues[j].intValue();
				}
				if (paramkeys[j].equals("z")) {
					zOffset=j;
					z=paramvalues[j].intValue();
				}
				if (paramkeys[j].equals("ch") || paramkeys[j].equals("channels")) {
					chOffset=ch;
					ch=paramvalues[j].intValue();
				}
			}
         //IJ.write (x + "," + y + ", " + z);
			//IJ.write (format+","+sign+","+compression+","+intelByteOrder);
			// set Calibration based on ics-derived values
			if (scalekeysFound) {
				this.cal=new Calibration();
				this.cal.setUnit(scalekeys[xOffset]);
				// we'll have to assume that the unit is the same for x,y, and z
				this.cal.pixelWidth=scalevalues[xOffset].doubleValue();
				this.cal.pixelHeight=scalevalues[yOffset].doubleValue();
				this.cal.pixelDepth=scalevalues[zOffset].doubleValue();
			}

			// for version 2 ics files, the ids file is the same as the ics one:

			if (ics_version.compareTo(new Float(1.0)) > 0)  {
				this.idsFileName=this.icsFileName;
			}
                        else { 
                        	// for version 1 we need an ids file,check if it exists
				File f = new File(this.directory, this.idsFileName); 
				if (!f.canRead()) {
					IJ.showMessage("ICS Opener", "\""+this.idsFileName+"\" not found");
					FileInfo[] fib=new FileInfo[0];
                                        return fib;
				}
			}

			FileInfo[] fia=new FileInfo[ch];
			// construct a FileInfo for every channel
			for (int k=0;k<ch;k++) {
				fia[k]=new FileInfo();
				fia[k].fileFormat=fia[k].RAW;
				fia[k].fileName=this.idsFileName;
				fia[k].directory=this.directory;
				fia[k].width=x;
				fia[k].height=y;
				fia[k].offset=offset.intValue()+k*(x*y*z*t*bits/8);
				//Integer Ioffset=new Integer(fia[k].offset);
				fia[k].nImages=z*t;
				if (intelByteOrder)
					fia[k].intelByteOrder=true;
				else 
					fia[k].intelByteOrder=false;
				fia[k].whiteIsZero=false;
				if (bits==8)
					fia[k].fileType=FileInfo.GRAY8;
				else if ((bits==16)&&(sign.equals("signed"))) 
					fia[k].fileType=FileInfo.GRAY16_SIGNED;
				else if ((bits==16)&&(sign.equals("unsigned"))) 
					fia[k].fileType=FileInfo.GRAY16_UNSIGNED;
				else if ((bits==32)&&(format.equals("float"))) 
					fia[k].fileType=FileInfo.GRAY32_FLOAT;
				else if ((bits==32)&&(format.equals("real"))) 
					fia[k].fileType=FileInfo.GRAY32_FLOAT;
				else if ((bits==32)&&(sign.equals("signed"))) 
					fia[k].fileType=FileInfo.GRAY32_INT;
				else if ((bits==32)&&(sign.equals("unsigned"))) 
					fia[k].fileType=FileInfo.GRAY32_INT;
				else
					fia[k].fileType=FileInfo.GRAY8;
					
			}	
			return fia;
		}

		public void OpenIcs () {
			FileInfo[] fi=IcsInfo();
         ImagePlus[] ipStack = new ImagePlus[fi.length];
			if (fi==null)
				return;
         IJ.showStatus ("Opening ICS file...");
         if (!interlaced) {
            for (int i=0; i<fi.length; i++) {
               //IJ.write (fi[i].intelByteOrder+"," + fi[i].fileType);
               FileOpener fo=new FileOpener(fi[i]);
               Integer chCount=new Integer(i+1);
               // only show when not interlaced
               ImagePlus ip=fo.open(!interlaced);
               if (ip==null) return;
               // Only used for de-interlacing:
               if (fi.length<=1)
                  ip.setTitle(fi[i].fileName.substring(0,fi[i].fileName.length()-4));
               else
                  ip.setTitle(fi[i].fileName.substring(0,fi[i].fileName.length()-4)+" ch: "+chCount.toString());
               ip.setCalibration(this.cal);
               ipStack[i]=ip;
               IJ.showProgress(i+1, fi.length);
            }
			}
         // Deinterlace if interlaced
         // stackOld contains ch1 (t,z ??), ch2 (t,z ??) etc..
         if (interlaced) {
            IJ.showStatus ("Deinterlacing ICS file...");
            int width = fi[0].width;
            int height = fi[0].height;
            fi[0].width = fi[0].width * fi.length;
            FileOpener f=new FileOpener(fi[0]);
            // only show when not interlaced
            ImagePlus ip=f.open(false);
            if (ip==null) return;
            // Only used for de-interlacing:
            ip.setCalibration(this.cal);
            // Assemble the opened images in one big stack
            ImageStack stackOld = ip.getStack();

            int dimension = fi[0].width * fi[0].height;
            int nChannels = fi.length;
            int nSlices = ip.getImageStackSize();
            short[] pixelsOld; //old stack data is read and processed one slice at a time
            short[][][] pixelsNew = new short[nSlices][nChannels][dimension];
            int xNew, yNew;
            // get pixels from old stack and deinterlace into new array
            for (int slice=1; slice<=nSlices; slice++) {
               //IJ.write(slice + " ");
               pixelsOld = (short[])stackOld.getPixels(slice); 
               int pixNum = 0;
               for (int ch=0; ch < nChannels; ch++) {
                  for (int j=0; j< (dimension); j++) { 
                     xNew=pixNum%(nChannels); 
                     yNew=(int)Math.floor(pixNum/(nChannels) ); 
                     //IJ.write(xNew + ", " + yNew);
                     // short value = pixelsOld[j];
                     //if (yNew >= (width * height))
                        //IJ.write (yNew + ", " + ch + ", " + pixNum);
                     pixelsNew[slice-1][xNew][yNew]=pixelsOld[j]; 
                     pixNum++;  
                  }
               } 
               IJ.showProgress(slice, nSlices);
            }
            // order in the new de-interlaced array is still the same:
            // ch1 (t,z ???) ch2 (t,z ???), etc..
            // create new Image stack 
            // when there are more than MAX_CHANNELS present them in a single stack
            // else split them out
            if (nChannels >= MAX_CHANNELS) {
               FileOpener fo=new FileOpener(fi[0]);
               ImageStack stackNew = new ImageStack(width, height, fo.createColorModel(fi[0]));
               for (int slice=0; slice<nSlices; slice++) {
                  Integer sliceCount=new Integer(slice+1);
                  for (int ch=0; ch < nChannels; ch++) {
                     stackNew.addSlice(sliceCount.toString(),pixelsNew[slice][ch]);
                  }
                  IJ.showProgress(slice, nSlices);
               }                                                                      
               ImagePlus impNew = new ImagePlus(fi[0].fileName.substring(0,fi[0].fileName.length()-4)+" ch: ", stackNew);
               impNew.getProcessor().resetMinAndMax();
               impNew.show();
            } else {  // less than MAX_CHANNELS channels
					GenericDialog gd = new GenericDialog("Select channels");
					String[] labels = new String[nChannels];
					boolean[] defaults = new boolean[nChannels];
					for (Integer ch=0; ch < nChannels; ch++) {
						Integer chCount = new Integer(ch+1);
						labels[ch] = "Channel: " + chCount.toString();
						defaults[ch] = false;
					}
					gd.addCheckboxGroup(nChannels, 1, labels, defaults);
					gd.showDialog();
					if (gd.wasCanceled()) return;

					Integer nChannelsSelected = 0; 
					for (int ch=0; ch < nChannels; ch++) { 
						if (!gd.getNextBoolean()) {
							// Check if user selected this channel.
							continue;
						}
						nChannelsSelected++;
                  Integer chCount=new Integer(ch+1);
                  FileOpener fo=new FileOpener(fi[ch]);
                  ImageStack stackNew = new ImageStack(width, height,fo.createColorModel(fi[ch]));
                  // assign new pixel array to new stack                                       
                  for (int i=0; i< nSlices; i++) { 
                     Integer sliceCount=new Integer(i+1);
                     stackNew.addSlice(sliceCount.toString(),pixelsNew[i][ch]);
                  }                                                                      

						String filename = fi[ch].fileName.substring(0,fi[ch].fileName.length()-4)+" ch "+chCount.toString()+".tif";

                  ImagePlus impNew = new ImagePlus(filename, stackNew);

						FileSaver fileSaver = new FileSaver(impNew);
						File file = new File(this.directory, filename);
						fileSaver.saveAsTiff(file.getPath());

                  impNew.getProcessor().resetMinAndMax();
                  //impNew.show();
               }
					if (nChannelsSelected > 0) {
						IJ.showMessage("Converted " + nChannelsSelected.toString() + " channels of all selected Ics images to TIFF files");
					}
            }
         }
         IJ.showStatus ("Done opening ICS file...");
		}

	}
}
// vim: ts=3 sts=3 sw=3
