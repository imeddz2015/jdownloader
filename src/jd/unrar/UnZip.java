package jd.unrar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import jd.utils.JDUtilities;

public class UnZip {
	protected ZipFile zipF;

	/** The buffer for reading/writing the ZipFile data */
	protected byte[] b;
	/**
	 * Der Ziehpfand in dem entpackt werden soll
	 */
	private File targetPath = null;
	private File zipFile = null;
	public boolean autoDelete = false;

	/**
	 * Konstruktor in dem nur das zipFile angegeben wird
	 * 
	 * @param zipFile
	 */

	public UnZip(File zipFile) {
		this(zipFile, null);
	}

	/**
	 * Konstruktor mit einem bestimmten Ziel
	 * 
	 * @param zipFile
	 * @param targetPath
	 */
	public UnZip(File zipFile, File targetPath) {
		b = new byte[8092];
		this.zipFile = zipFile;
		if (targetPath == null)
			this.targetPath = zipFile.getParentFile();
		else
			this.targetPath = targetPath;

	}

	@SuppressWarnings("unchecked")
	protected SortedSet dirsMade;

	@SuppressWarnings("unchecked")
	public String[] listFiles() {
		try {
			zipF = new ZipFile(zipFile);
			Enumeration all = zipF.entries();
			LinkedList<String> ret = new LinkedList<String>();
			while (all.hasMoreElements()) {
				ret.add(((ZipEntry) all.nextElement()).getName());
			}
			return ret.toArray(new String[ret.size()]);

		} catch (IOException err) {
			err.printStackTrace();
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public File[] extract() {
		dirsMade = new TreeSet();
		try {
			zipF = new ZipFile(zipFile);
	
			Enumeration all = zipF.entries();
			LinkedList<File> ret = new LinkedList<File>();
			while (all.hasMoreElements()) {
				File file = getFile((ZipEntry) all.nextElement());
				if(file!=null)ret.add(file);
			}
			if(autoDelete)zipFile.delete();
			return ret.toArray(new File[ret.size()]);
		} catch (IOException err) {
			err.printStackTrace();
		}
		return null;
	}

	protected boolean warnedMkDir = false;

	@SuppressWarnings("unchecked")
	protected File getFile(ZipEntry e) throws IOException {
		String zipName = e.getName();
			if (zipName.startsWith("/")) {
				if (!warnedMkDir)
					JDUtilities.getLogger().info("Ignoring absolute paths");
				warnedMkDir = true;
				zipName = zipName.substring(1);
			}
			if (zipName.endsWith("/")) {
				return null;
			}
			int ix = zipName.lastIndexOf('/');
			if (ix > 0) {
				String dirName = zipName.substring(0, ix);
				if (!dirsMade.contains(dirName)) {
					File d = new File(targetPath, dirName);
					if (!(d.exists() && d.isDirectory())) {
						if (!d.mkdirs()) {
							JDUtilities.getLogger().severe("Warning: unable to mkdir "
									+ dirName);
						}
						dirsMade.add(dirName);
					}
				}
			}
			JDUtilities.getLogger().info("Creating " + zipName);
			File toExtract = new File(targetPath,zipName);
			FileOutputStream os = new FileOutputStream(toExtract);
			InputStream is = zipF.getInputStream(e);
			int n = 0;
			while ((n = is.read(b)) > 0)
				os.write(b, 0, n);
			is.close();
			os.close();
			return toExtract;
		}

}