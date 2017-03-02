package org.rundeck.client.tool.commands.jobs;

import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.simplifyops.toolbelt.Command;
import com.simplifyops.toolbelt.CommandOutput;
import com.simplifyops.toolbelt.InputError;
import okhttp3.RequestBody;
import org.rundeck.client.api.RundeckApi;
import org.rundeck.client.api.model.JobFileItem;
import org.rundeck.client.api.model.JobFileItemList;
import org.rundeck.client.api.model.JobFileUploadResult;
import org.rundeck.client.api.model.Paging;
import org.rundeck.client.tool.RdApp;
import org.rundeck.client.tool.commands.AppCommand;
import org.rundeck.client.tool.options.PagingResultOptions;
import org.rundeck.client.util.Client;

import java.io.File;
import java.io.IOException;

/**
 * @author greg
 * @since 3/1/17
 */
@Command(description = "List and manage File options for Jobs.")
public class Files extends AppCommand {

    public Files(final RdApp rdApp) {
        super(rdApp);
    }


    @CommandLineInterface(application = "info") interface FileInfoOpts {

        @Option(shortName = "i", longName = "id", description = "File ID")
        String getId();
    }

    @Command(description = "Get info about a Job input option file (API v19)")
    public void info(FileInfoOpts opts, CommandOutput out) throws IOException, InputError {
        JobFileItem jobFileItem = apiCall(api -> api.getJobFileInfo(opts.getId()));
        out.output(jobFileItem);
    }

    @CommandLineInterface(application = "list") interface FileListOpts extends PagingResultOptions {
        @Option(shortName = "j", longName = "jobid", description = "Job ID")
        String getJobId();

        boolean isJobId();

        @Option(shortName = "e", longName = "eid", description = "Execution ID")
        String getExecId();

        boolean isExecId();

        @Option(shortName = "s",
                longName = "state",
                description = "File state filter for listing Files for a Job only. (default:temp), one of: temp," +
                              "expired,deleted,retained.")
        FileState getFileState();

        boolean isFileState();
    }

    static enum FileState {
        temp,
        expired,
        deleted,
        retained
    }

    @Command(description = "List files uploaded for a Job or Execution (API v19). Specify Job ID or Execution ID")
    public void list(FileListOpts opts, CommandOutput out) throws IOException, InputError {

        if (!opts.isJobId() && !opts.isExecId() || opts.isExecId() && opts.isJobId()) {
            throw new InputError("One of -j/--jobid or -e/--eid is required");
        }
        if (opts.isExecId() && opts.isFileState()) {
            throw new InputError("-s/--state not a valid option for -e/--eid");
        }
        int offset = opts.isOffset() ? opts.getOffset() : 0;
        int max = opts.isMax() ? opts.getMax() : 20;

        JobFileItemList result;
        if (opts.isJobId()) {
            result = apiCall(api -> api.listJobFiles(
                    opts.getJobId(),
                    opts.isFileState() ? opts.getFileState().toString() : null,
                    offset,
                    max
            ));
        } else {
            result = apiCall(api -> api.listExecutionFiles(opts.getExecId(), offset, max));
        }

        Paging paging = result.getPaging();

        if (paging != null) {
            out.info(paging);
        }

        out.output(result.getFiles());


        if (paging != null && paging.hasMoreResults()) {
            out.info(paging.moreResults("-o"));
        }
    }

    @CommandLineInterface(application = "load") interface FileUploadOpts {

        @Option(shortName = "i", longName = "id", description = "Job ID")
        String getId();

        @Option(shortName = "o", longName = "option", description = "Option name")
        String getOption();

        @Option(shortName = "f",
                longName = "file",
                description =
                        "File path of the file to upload (load command) or destination for storing the jobs (list " +
                        "command)")
        File getFile();
    }

    @Command(description = "Upload a file as input for a job option (API v19). Returns a unique key for the uploaded" +
                           " file, which can be used as the option value when running the job.")
    public boolean load(FileUploadOpts options, CommandOutput out) throws IOException, InputError {
        File input = options.getFile();
        if (!input.canRead() || !input.isFile()) {
            throw new InputError(String.format("File is not readable or does not exist: %s", input));
        }

        String fileName = input.getName();
        JobFileUploadResult jobFileUploadResult = uploadFileForJob(
                getClient(),
                input,
                options.getId(),
                options.getOption()
        );

        String fileid = jobFileUploadResult.getFileIdForOption(options.getOption());
        if (null != fileid) {
            out.info("File " + fileName + " uploaded successfully for option " + options.getOption());
            out.info("File key:");
            out.output(fileid);
            return true;
        } else {
            out.error(String.format("Expected one option result for option %s, but saw: ", options.getOption()));
        }
        out.output(jobFileUploadResult);
        return false;
    }

    /**
     * Upload a file for a job option input and return the result
     *
     * @param client
     * @param input
     * @param jobId
     * @param optionName
     *
     * @return
     *
     * @throws InputError
     * @throws IOException
     */
    public static JobFileUploadResult uploadFileForJob(
            final Client<RundeckApi> client,
            final File input,
            final String jobId,
            final String optionName
    ) throws InputError, IOException
    {
        if (!validInputFile(input)) {
            throw new IOException("Can't read file: " + input);
        }
        RequestBody requestBody = RequestBody.create(Client.MEDIA_TYPE_OCTET_STREAM, input);
        return apiCall(
                client,
                api -> api.uploadJobOptionFile(
                        jobId,
                        optionName,
                        input.getName(),
                        requestBody
                )
        );
    }

    /**
     * @param input
     *
     * @return true if the file can be read
     */
    public static boolean validInputFile(final File input) {
        return input.exists() && input.canRead() && input.isFile();
    }
}
