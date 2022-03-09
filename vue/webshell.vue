<template>
  <div id="terminal" ref="terminal"></div>
</template>

<script>
import { Terminal } from 'xterm'
import { FitAddon } from 'xterm-addon-fit'
import { AttachAddon } from 'xterm-addon-attach'
import 'xterm/css/xterm.css'
import 'xterm/lib/xterm.js'

import $ from 'jquery'
import Cookies from 'js-cookie'

export default {
  data() {
    return {
      websock: '',
      fitAddon: '',
      attachAddon: '',
      term: '',
      pod: {},
      protocol: null,
      socketURL: '',
      socket: null,
      terminalContainer: null,
      // 填写你的pod信息
      podName: '',
      namespace: '',
      container: '',
      masterUrl: '',
      msg: {}
    }
  },
  beforeDestroy() {
    this.socket.close()
    this.term.dispose()
  },
  created() {},

  mounted() {
    const that = this
    const params = JSON.stringify({
      id: that.$route.query.id
    })

    that.terminalContainer = document.getElementById('terminal-container')
  },
  methods: {
    // 初始化连接
    initTerm() {
      const that = this
      const height = window.innerHeight - 140
      const term = new Terminal({
        cursorBlink: true, // 光标闪烁
        // 初始化时，获取容器宽度，计算容器显示的列数
        cols: parseInt(document.getElementById('terminal').clientWidth / 9),
        rows: parseInt(height / 17, 10),
        fontSize: 16, // 字体大小
        theme: {
          foreground: 'yellow', // 字体
          background: '#060101', // 背景色
          cursor: 'help' // 设置光标
        }
      })
      // 打开容器
      term.open(this.$refs['terminal'], {
        cursorBlink: true
      })
      // fit插件适应容器显示内容，使其自适应包裹
      this.fitAddon = new FitAddon()
      term.loadAddon(this.fitAddon)
      this.fitAddon.fit()
      // 监听窗口大小，可以自适应容器内容的换行显示
      window.addEventListener('resize', this.resizeScreen)
      // 向容器中输入内容，只执行一次
      term.writeln('Welcome to use 天机算AI!')
      // 容器中的命令行输入内容，向后端发送
      term.onData(function (data) {
        that.sendCmdMessage(data)
      })
      that.term = term

      that.initWebSocket(
        that.namespace,
        that.podName,
        that.container,
        that.masterUrl
      )
    },

    resizeScreen() {
      // 不传size
      this.fitAddon.fit()
      // 窗口变化结束后，获取大小计算宽高
      this.$nextTick(() => {
        var cols = document.getElementById('terminal').clientWidth
        var rows = document.getElementById('terminal').clientHeight
        this.term.resize(parseInt(cols / 9), 27)
        this.sendSizeMessage({
          Op: 'resize',
          Cols: this.term.cols,
          Rows: this.term.rows
        })
      })
    },

    initWebSocket(namespace, pod, container, masterUrl) {
      this.msg.type = 'connect'
      this.msg.namespace = namespace
      this.msg.pod = pod
      this.msg.container = container
      this.msg.masterUrl = masterUrl
      this.msg.rows = this.term.rows
      this.msg.cols = this.term.cols

      var protocol = location.protocol === 'https:' ? 'wss://' : 'ws://'
      this.socketURL = protocol + 'localhost:8289/container/terminal/shell/ws'
      // this.socketURL +=
      //   '?name=' +
      //   this.name +
      //   '&namespace=' +
      //   this.namespace +
      //   '&container=' +
      //   this.container +
      //   '&cols=' +
      //   this.term.cols +
      //   '&rows=' +
      //   this.term.rows

      // websocket 链接
      this.socket = new WebSocket(this.socketURL)
      /**
       * 连接成功后的回调函数
       * @type {onOpen}
       */
      this.socket.onopen = this.onOpen
      /**
       * 收到服务器数据后的回调函数
       * @type {onMessage}
       */
      this.socket.onmessage = this.onMessage
      /**
       * 报错时的回调函数
       * @type {onError}
       */
      this.socket.onerror = this.onError
      /**
       * 连接关闭后的回调函数
       * @type {onClose}
       */
      this.socket.onclose = this.onClose
      // return webSocket();
    },
    /**
     * 初始化链接
     */
    onOpen(event) {
      this.sendConnMessage()
    },
    /**
     * 收到服务器数据后的回调函数
     * @type {onMessage}
     */
    onMessage(event) {
      this.term.write(event.data)
    },

    sendConnMessage() {
      this.doSend(JSON.stringify(this.msg))
    },

    doSend(data) {
      //数据发送
      this.socket.send(data)
    },
    /**
     * 向服务器发送消息
     */
    sendCmdMessage(cmd) {
      var msg = {}
      msg.type = 'send'
      msg.cmd = cmd
      // if (isType.type == 'kubernetes') {
      msg.cmd = cmd
      // } else {
      //   msg.command = cmd
      // }
      console.log(msg)
      this.doSend(JSON.stringify(msg))
    },
    /**
     * 改变行和列数
     */
    sendSizeMessage(cmd) {
      var msg = {}
      msg.type = 'resize'
      msg.cmd = JSON.stringify(cmd)
      this.doSend(JSON.stringify(msg))
    }
  }
}
</script>

<style lang="less">
* {
  margin: 0;
  padding: 0;
}

html,
body {
  font-size: 18px;
  width: 100%;
  height: 100%;
}

#terminal-container {
  height: 1200px;
  margin: 0 auto;
  padding: 2px;
}

#terminal-container .terminal {
  background-color: #111;
  color: #fafafa;
  padding: 2px;
}

#terminal-container .terminal:focus .terminal-cursor {
  background-color: #fafafa;
}
</style>