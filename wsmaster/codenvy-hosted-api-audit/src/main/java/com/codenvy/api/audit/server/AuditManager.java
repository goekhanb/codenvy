/*
 *  [2012] - [2016] Codenvy, S.A.
 *  All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Codenvy S.A. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Codenvy S.A.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Codenvy S.A..
 */
package com.codenvy.api.audit.server;

import com.codenvy.api.license.CodenvyLicense;
import com.codenvy.api.license.LicenseException;
import com.codenvy.api.license.server.CodenvyLicenseManager;
import com.codenvy.api.permission.server.PermissionsManager;
import com.codenvy.api.permission.server.model.impl.AbstractPermissions;
import com.google.common.annotations.VisibleForTesting;

import org.apache.commons.io.FileUtils;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.Page;
import org.eclipse.che.api.core.Page.PageRef;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.user.server.UserManager;
import org.eclipse.che.api.user.server.model.impl.UserImpl;
import org.eclipse.che.api.workspace.server.WorkspaceManager;
import org.eclipse.che.api.workspace.server.model.impl.WorkspaceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.Files.createTempDirectory;
import static java.util.Collections.emptyList;

/**
 * Facade for audit report related operations.
 *
 * @author Igor Vinokur
 */
@Singleton
public class AuditManager {

    private static final Logger LOG = LoggerFactory.getLogger(AuditManager.class);

    private final WorkspaceManager      workspaceManager;
    private final PermissionsManager    permissionsManager;
    private final CodenvyLicenseManager licenseManager;
    private final AuditReportPrinter    reportPrinter;
    private final UserManager           userManager;

    private AtomicBoolean inProgress = new AtomicBoolean(false);

    @Inject
    public AuditManager(UserManager userManager,
                        WorkspaceManager workspaceManager,
                        PermissionsManager permissionsManager,
                        CodenvyLicenseManager licenseManager,
                        AuditReportPrinter reportPrinter) {
        this.userManager = userManager;
        this.workspaceManager = workspaceManager;
        this.permissionsManager = permissionsManager;
        this.licenseManager = licenseManager;
        this.reportPrinter = reportPrinter;
    }

    /**
     * Generates audit report.
     *
     * @return path of audit report file
     * @throws ServerException
     *         if an error occurs
     * @throws ConflictException
     *         if generating report is being run by other user
     */
    public Path generateAuditReport() throws ServerException, ConflictException, IOException {
        if (!inProgress.compareAndSet(false, true)) {
            throw new ConflictException("This command is being run by other user");
        }

        Path auditReport = null;
        try {
            String dateTime = new SimpleDateFormat("dd-MM-yyyy_hh:mm:ss").format(new Date());
            auditReport = createTempDirectory(null).resolve("report_" + dateTime + ".txt");
            Files.createFile(auditReport);

            CodenvyLicense license = null;
            try {
                license = licenseManager.load();
            } catch (LicenseException ignored) {
                //Continue printing report without license info
            }
            reportPrinter.printHeader(auditReport, userManager.getTotalCount(), license);
            printAllUsers(auditReport);
        } catch (Exception exception) {
            if (auditReport != null) {
                deleteReportDirectory(auditReport);
            }
            LOG.error(exception.getMessage(), exception);
            throw new ServerException(exception.getMessage(), exception);
        } finally {
            inProgress.set(false);
        }

        return auditReport;
    }

    private void printAllUsers(Path auditReport) throws ServerException {
        Page<UserImpl> currentPage = userManager.getAll(30, 0);
        do {
            //Print users with their workspaces from current page
            for (UserImpl user : currentPage.getItems()) {
                List<WorkspaceImpl> workspaces;
                int permissionsNumber;
                try {
                    workspaces = workspaceManager.getWorkspaces(user.getId());
                    permissionsNumber = workspaces.size();
                    //add workspaces witch are belong to user, but user doesn't have permissions for them.
                    workspaceManager.getByNamespace(user.getName())
                                    .stream()
                                    .filter(workspace -> !workspaces.contains(workspace))
                                    .forEach(workspaces::add);
                } catch (ServerException exception) {
                    reportPrinter.printError("Failed to receive list of related workspaces for user " + user.getId(), auditReport);
                    continue;
                }
                Map<String, List<AbstractPermissions>> wsPermissions = new HashMap<>();
                for (WorkspaceImpl workspace : workspaces) {
                    try {
                        wsPermissions.put(workspace.getId(), permissionsManager.getByInstance("workspace", workspace.getId()));
                    } catch (NotFoundException | ConflictException e) {
                        wsPermissions.put(workspace.getId(), emptyList());
                    }
                }
                reportPrinter.printUserInfoWithHisWorkspacesInfo(auditReport, user, workspaces, permissionsNumber, wsPermissions);
            }
            //Initialize next page if exist, otherwise stop printing report
            final Optional<PageRef> nextPageRefOpt = currentPage.getNextPageRef();
            if (nextPageRefOpt.isPresent()) {
                final PageRef nextPageRef = nextPageRefOpt.get();
                final long itemsBefore = nextPageRef.getItemsBefore();
                //TODO: fix when https://github.com/eclipse/che/issues/2524 will be resolved
                if (itemsBefore < Integer.MAX_VALUE) {
                    throw new ServerException("Skip count limit was reached while retrieving all users");
                }
                currentPage = userManager.getAll(nextPageRef.getPageSize(), (int)itemsBefore);
            } else {
                break;
            }
        } while (true);
    }

    @VisibleForTesting
    void deleteReportDirectory(Path auditReport) {
        try {
            FileUtils.deleteDirectory(auditReport.getParent().toFile());
        } catch (IOException exception) {
            LOG.error(exception.getMessage(), exception);
        }
    }
}