package cn.k8s.webssh.dto;

/**
 * @Author: wangxiaotong03
 * @Date: 2022/2/25 9:26
 */
public class ShellMessage {
// {"type":"connect",
// "namespace":"trainjob",
// "pod":"modeldev-8ac88f237ee68c2d017ee694c2e10000-79cdb55f64-wn7np",
// "container":"modeldev-8ac88f237ee68c2d017ee694c2e10000",
// "masterUrl":"https://10.110.114.13:6443",
// "rows":27,
// "cols":184}
    private String type;
    private String namespace;
    private String pod;
    private String container;
    private String masterUrl;
    private String rows;
    private String cols;
    private String cmd;

    public ShellMessage() {
    }

    public ShellMessage(String type, String namespace, String pod, String container, String masterUrl, String rows, String cols, String cmd) {
        this.type = type;
        this.namespace = namespace;
        this.pod = pod;
        this.container = container;
        this.masterUrl = masterUrl;
        this.rows = rows;
        this.cols = cols;
        this.cmd = cmd;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getPod() {
        return pod;
    }

    public void setPod(String pod) {
        this.pod = pod;
    }

    public String getContainer() {
        return container;
    }

    public void setContainer(String container) {
        this.container = container;
    }

    public String getMasterUrl() {
        return masterUrl;
    }

    public void setMasterUrl(String masterUrl) {
        this.masterUrl = masterUrl;
    }

    public String getRows() {
        return rows;
    }

    public void setRows(String rows) {
        this.rows = rows;
    }

    public String getCols() {
        return cols;
    }

    public void setCols(String cols) {
        this.cols = cols;
    }

    public String getCmd() {
        return cmd;
    }

    public void setCmd(String cmd) {
        this.cmd = cmd;
    }
}
