/**
 * 文件打包
 * 
 * @Author: qiongyue 
 * @Date: 2012/11/01
 */

import java.io.*;
import java.util.*;
import java.util.List;
import java.util.zip.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.URI;

class Tarball extends JFrame {
  private JButton submit = new JButton("打包"), cancel = new JButton("关闭"),
			openDir = new JButton("打开所在文件夹");

	private JLabel label = new JLabel("待打包的文件路径:");
	private JTextArea fileList = new JTextArea(20, 95);

	private String tempDir = null;
	private String rootDir = null;
	private String currentDir = null;
	private String tempTarballDir = "tarball_export";
	private List<String> processedFiles = new ArrayList<String>();
	private String lastZipFilename = null;
	private Map<String, String> configParams = new HashMap<String, String>();

	private ActionListener submitAction = new ActionListener() {
		public void actionPerformed(ActionEvent e) {
			String[] lines = fileList.getText().split("\n");
			int counter = 0;
			int sucessCounter = 0;
			int failCounter = 0;
			boolean canContainDir = false; // 开启目录打包

			try {
				clearTempDir();
			} catch (Exception mkdirErr) {
				mkdirErr.printStackTrace();
			}

			processedFiles.clear();

			for (String line : lines) {
				if (line.trim().equals("")) {
					continue;
				}

				if (counter == 0 && line.trim().substring(0, 2).equals("#!")) {
					rootDir = getAppDir(line);
					File t = new File(rootDir);

					// check
					if (!t.exists()) {
						JOptionPane.showMessageDialog(null, "程序目录不存在");
						return;
					}

					counter++;
					continue;
				}

				if (counter == 1 && line.trim().substring(0, 2).equals("#!")) {
					currentDir = getAppDir(line);

					File t = new File(currentDir);

					// check
					if (!t.exists()) {
						JOptionPane.showMessageDialog(null, "打包存放目录不存在");
						return;
					}

					counter++;
				}

				if (counter == 2 && line.trim().substring(0, 2).equals("#!")) {
					// canContainDir = true;
					configParams = parseParams(line.replace("#!", "").trim());
				}

				if (line.trim().substring(0, 1).equals("#"))
					continue;

				try {
					File file = new File(line.trim());

					if (file.isDirectory()) {
						if (configParams.get("dir") == null
								|| configParams.get("dir").equals("1") == false) {
							failCounter++;
							continue;
						}

						// 以.开头的目录跳过
						if (file.getName().substring(0, 1).equals('.'))
							continue;

						for (File tmpFile : getAllFiles(file)) {
							if (tmpFile.isFile()) {
								line = tmpFile.getAbsolutePath();
								if (tarFile(tmpFile) == true) {
									counter++;
									sucessCounter++;
								} else {
									failCounter++;
								}
							}
						}
					} else {
						if (tarFile(file) == true) {
							counter++;
							sucessCounter++;
						} else {
							failCounter++;
						}
					}

					System.out.println(line);
				} catch (Exception ex) {
					failCounter++;
					ex.printStackTrace();
				}
			}

			if (counter > 2) {
				if (true) {
					try {
						zip(new File(currentDir + tempTarballDir), new File(
								currentDir + getZipFilename()));
					} catch (Exception zipErr) {
						zipErr.printStackTrace();
					}
				}

				try {
					//清除临时文件
					//clearTempDir();
					deleteDir(new File(currentDir + tempTarballDir));
				} catch (Exception clearErr) {}
				
				
				JOptionPane.showMessageDialog(null, String.format(
						"成功打包%d个文件\n失败%d个\n生成更新包%s", sucessCounter,
						failCounter, lastZipFilename));
				
			} else {
				try {
					//清除临时文件
					//clearTempDir();
					deleteDir(new File(currentDir + tempTarballDir));
				} catch (Exception clearErr) {}
				
				JOptionPane.showMessageDialog(null, "无文件被打包");
			}

			// 保存配置
			Map<String, String> config = new HashMap<String, String>();
			config.put("fileList", fileList.getText());
			config.put("lastZipFilename", lastZipFilename);
			config.put("currentDir", currentDir);
			config.put("rootDir", rootDir);
			saveConfig(config);
		}
	};

	private ActionListener cancelAction = new ActionListener() {
		public void actionPerformed(ActionEvent e) {
			dispose();
			System.exit(0);
		}
	};

	private ActionListener openDirAction = new ActionListener() {
		public void actionPerformed(ActionEvent e) {
			try {
				if (System.getProperty("os.name").indexOf("Linux") != -1) {
					Runtime.getRuntime().exec("nautilus " + currentDir);
					return ;
				}
				
				if (lastZipFilename != null && !lastZipFilename.equals("")) {
					Runtime.getRuntime().exec(
							"explorer.exe /select, " + currentDir
									+ lastZipFilename);
				} else {
					Runtime.getRuntime().exec("explorer.exe " + currentDir);
				}
			} catch (Exception runErr) {
				runErr.printStackTrace();
			}

			System.out.println(currentDir);
		}
	};

	protected String getAppDir(String comment) {
		String path = rtrim(
				comment.trim().replace("#!", "").trim().split(": ")[1].trim(),
				File.separator.charAt(0))
				+ File.separator;

		return path;
	}

	protected List<File> getAllFiles(File dir) {
		List<File> files = new ArrayList<File>();

		if (!dir.isDirectory() || dir.getName().substring(0, 1).equals('.')
				|| dir.isHidden()) {
			return files;
		}

		for (File file : dir.listFiles()) {
			if (file.isFile()) {
				files.add(file);
			} else if (file.isDirectory()) {
				files.addAll(getAllFiles(file));
			}
		}

		return files;

	}

	public Map<String, String> parseParams(String urlString) {
		Map<String, String> data = new HashMap<String, String>();

		for (String item : urlString.split("&")) {
			String[] map = item.split("=");
			if (map.length < 2 || map[0].equals(""))
				continue;

			data.put(map[0], map[1]);
		}

		return data;
	}

	protected String getZipFilename() {
		File dir = new File(currentDir);
		Calendar c = Calendar.getInstance();

		final String stamp = String.format(
				"%s%04d-%02d-%02d",
				(configParams.get("name") == null || configParams.get("name")
						.equals("")) ? tempTarballDir : configParams
						.get("name"), c.get(Calendar.YEAR), c
						.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));

		String[] zipFiles = dir.list(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				if (name.indexOf(stamp) != -1 && name.endsWith(".zip")) {
					return true;
				}

				return false;
			}
		});

		int number = 1;
		if (zipFiles.length > 0) {
			number = zipFiles.length + 1;
			while (Arrays.asList(dir.list()).contains(
					String.format("%s-%03d.zip", stamp, number))) {
				number++;
			}
		}

		return lastZipFilename = String.format("%s-%03d.zip", stamp, number);
	}

	protected boolean tarFile(File source) throws Exception {
		String filefullpath = source.getAbsolutePath();

		if (processedFiles.contains(filefullpath)) {
			return false;
		}

		processedFiles.add(filefullpath);

		if (source.exists()) {
			if (source.isFile()) {
				if (filefullpath.indexOf(rootDir) == -1) {
					return false;
				}

				String relPath = (source.getParent() + File.separator).replace(
						rootDir, "");

				System.out.println(rootDir);
				System.out.println(relPath);
				System.out.println(currentDir);

				File descDir = new File(currentDir + tempTarballDir
						+ File.separator + relPath);
				if (!descDir.exists()) {
					try {
						descDir.mkdirs();
					} catch (Exception e) {
						e.printStackTrace();
						return false;
					}
				}

				File desc = new File(descDir.getAbsolutePath() + File.separator
						+ source.getName());

				FileInputStream fis = null;
				FileOutputStream fos = null;

				try {
					fis = new FileInputStream(source);
					fos = new FileOutputStream(desc);

					byte[] buf = new byte[(int) source.length()];
					fis.read(buf);
					fos.write(buf);
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					try {
						fis.close();
						fos.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

			} else {
				throw new Exception(filefullpath + " 不支持目录打包");
			}
		} else {
			throw new Exception(filefullpath + " 不存在");
		}

		return true;
	}

	protected void zip(File directory, File zipfile) throws IOException {
		URI base = directory.toURI();
		Deque<File> queue = new LinkedList<File>();
		queue.push(directory);
		OutputStream out = new FileOutputStream(zipfile);
		Closeable res = out;
		try {
			ZipOutputStream zout = new ZipOutputStream(out);
			res = zout;
			while (!queue.isEmpty()) {
				directory = queue.pop();
				for (File kid : directory.listFiles()) {
					String name = base.relativize(kid.toURI()).getPath();
					if (kid.isDirectory()) {
						queue.push(kid);
						name = name.endsWith("/") ? name : name + "/";
						zout.putNextEntry(new ZipEntry(name));
					} else {
						zout.putNextEntry(new ZipEntry(name));
						copy(kid, zout);
						zout.closeEntry();
					}
				}
			}
		} finally {
			res.close();
		}
	}

	private static void copy(InputStream in, OutputStream out)
			throws IOException {
		byte[] buffer = new byte[1024];
		while (true) {
			int readCount = in.read(buffer);
			if (readCount < 0) {
				break;
			}
			out.write(buffer, 0, readCount);
		}
	}

	private static void copy(File file, OutputStream out) throws IOException {
		InputStream in = new FileInputStream(file);
		try {
			copy(in, out);
		} finally {
			in.close();
		}
	}

	private static void copy(InputStream in, File file) throws IOException {
		OutputStream out = new FileOutputStream(file);
		try {
			copy(in, out);
		} finally {
			out.close();
		}
	}

	protected boolean clearTempDir() throws Exception {
		File dir = new File(currentDir + tempTarballDir
				+ System.getProperty("file.separator"));

		if (dir.exists()) {
			deleteDir(dir);
		} 
		
		try {
			dir = new File(currentDir + tempTarballDir
					+ System.getProperty("file.separator"));
			
			dir.mkdir();
		} catch (SecurityException e) {
			e.printStackTrace();
			throw new Exception("mkdir error: " + e.getMessage());
		}
		

		return true;
	}

	protected void deleteDir(File dir) throws Exception {
		// 清除文件
		File files[] = dir.listFiles();
		try {			
			for (File file : files) {
				if (file.isDirectory()) {
					if (file.list().length > 0) {
						deleteDir(file);
					} else {
						file.delete();
					}
				} else if (file.isFile()) {
					file.delete();
				}
			}
			
			if (dir != null)
				dir.delete();
		} catch (Exception e) {
			e.printStackTrace();
			throw new Exception(e.getMessage());
		}
	}

	protected static String rtrim(String s, char c) {
		int i = s.length() - 1;
		while (i > 0 && s.charAt(i) == c) {
			i--;
		}
		return s.substring(0, i + 1);
	}

	protected static String rtrim(String s) {
		int i = s.length() - 1;

		while (i > 0 && Character.isWhitespace(s.charAt(i))) {
			i--;
		}
		return s.substring(0, i + 1);
	}

	protected Map<String, String> loadConfig() {
		Map<String, String> config = new HashMap<String, String>();

		// InputStream is =
		// this.getClass().getClassLoader().getResourceAsStream("/tarball.properties");

		Properties p = new Properties();
		try {
			InputStream is = new FileInputStream(tempDir + ".tarball.properties");
			p.load(is);

			Set<String> set = p.stringPropertyNames();
			Iterator<String> iteratorHash = set.iterator();
			while (iteratorHash.hasNext()) {
				String key = iteratorHash.next();
				String value = p.getProperty(key);
				config.put(key, value);
			}

			is.close();
		} catch (Exception e) {
			config.put("listFile", "");

			e.printStackTrace();
		}

		return config;
	}

	protected void saveConfig(String data) {
		Properties p = new Properties();
		p.put("fileList", data);

		try {
			p.store(new FileOutputStream(tempDir + ".tarball.properties"), null);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	protected void saveConfig(Map<String, String> config) {
		Properties p = new Properties();

		Iterator<String> iteratorHash = config.keySet().iterator();
		while (iteratorHash.hasNext()) {
			Object key = iteratorHash.next();
			p.put(key.toString(), config.get(key).toString());
		}

		try {
			p.store(new FileOutputStream(tempDir + ".tarball.properties"), null);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public Tarball() {
		submit.addActionListener(submitAction);
		cancel.addActionListener(cancelAction);
		openDir.addActionListener(openDirAction);

		fileList.setFont(new Font("宋体", java.awt.Font.PLAIN, 16));
		fileList.setText("#! 程序根目录: F:\\www\\erp\n#! 打包存放目录: F:\\www\n#! name=qiongyue&dir=0\n# 以#开头代表这一行为备注,默认不支持目录打包\n\n");

		//tempDir = System.getProperty("java.io.tmpdir");

		currentDir = System.getProperty("user.dir")
				+ System.getProperty("file.separator");
		
		tempDir = System.getProperty("user.home")
				+ System.getProperty("file.separator");

		Map<String, String> config = loadConfig();
		if (config.get("fileList") != null) {
			fileList.setText(config.get("fileList").toString());
		}

		if (config.get("lastZipFilename") != null) {
			lastZipFilename = config.get("lastZipFilename").toString();
		}

		if (config.get("currentDir") != null) {
			currentDir = config.get("currentDir").toString();
		}

		setLayout(new FlowLayout());

		add(label);
		add(new JScrollPane(fileList));
		add(submit);
		if (System.getProperty("os.name").indexOf("Windows") != -1 || System.getProperty("os.name").indexOf("Linux") != -1)
			add(openDir);
		add(cancel);

		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		// Dimension frameSize = this.getPreferredSize();//获取当前窗口大小
		this.setLocation((screenSize.width - 800) / 2,
				(screenSize.height - 480) / 2);
	}

	public static void run(final JFrame f, final int width, final int height) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				// f.setTitle(f.getClass().getSimpleName());
				f.setTitle("文件打包工具0.2");
				f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				f.setSize(width, height);
				f.setVisible(true);

				f.setResizable(false);
			}
		});
	}

	public static void main(String[] args) {
		run(new Tarball(), 800, 480);

	}
}
