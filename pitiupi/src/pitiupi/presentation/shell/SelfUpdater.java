package pitiupi.presentation.shell;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import java.awt.Component;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import pitiupi.errors.ErrorCode;
import pitiupi.errors.PitiupiException;
import pitiupi.platform.AppVersion;
import pitiupi.platform.PitiupiHome;
import pitiupi.plugins.remote.*;

