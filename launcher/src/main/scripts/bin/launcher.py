#!/usr/bin/env python

import errno
import os
import platform
import sys
import traceback

from fcntl import flock, LOCK_EX, LOCK_NB
from argparse import ArgumentParser
from os import O_RDWR, O_CREAT, O_WRONLY, O_APPEND
from os.path import basename, dirname, exists, realpath
from os.path import join as pathjoin
from signal import SIGTERM, SIGKILL
from stat import S_ISLNK
from time import sleep

LSB_NOT_RUNNING = 3
LSB_STATUS_UNKNOWN = 4


def find_install_path(f):
    """Find canonical parent of bin/launcher.py"""
    if basename(f) != 'launcher.py':
        raise Exception("Expected file '%s' to be 'launcher.py' not '%s'" % (f, basename(f)))
    p = realpath(dirname(f))
    if basename(p) != 'bin':
        raise Exception("Expected file '%s' directory to be 'bin' not '%s" % (f, basename(p)))
    return dirname(p)


def makedirs(p):
    """Create directory and all intermediate ones"""
    try:
        os.makedirs(p)
    except OSError as e:
        if e.errno != errno.EEXIST:
            raise


def load_properties(f):
    """Load key/value pairs from a file"""
    properties = {}
    for line in load_lines(f):
        k, v = line.split('=', 1)
        properties[k.strip()] = v.strip()
    return properties


def load_lines(f):
    """Load lines from a file, ignoring blank or comment lines"""
    lines = []
    for line in open(f, 'r').readlines():
        line = line.strip()
        if len(line) > 0 and not line.startswith('#'):
            lines.append(line)
    return lines


def try_lock(f):
    """Try to open an exclusive lock (inheritable) on a file"""
    try:
        flock(f, LOCK_EX | LOCK_NB)
        return True
    except (IOError, OSError):  # IOError in Python 2, OSError in Python 3.
        return False


def open_read_write(f, mode):
    """Open file in read/write mode (without truncating it)"""
    return os.fdopen(os.open(f, O_RDWR | O_CREAT, mode), 'r+')


class Process:
    def __init__(self, path):
        makedirs(dirname(path))
        self.path = path
        self.pid_file = open_read_write(path, 0o600)
        self.refresh()

    def refresh(self):
        self.locked = try_lock(self.pid_file)

    def clear_pid(self):
        assert self.locked, 'pid file not locked by us'
        self.pid_file.seek(0)
        self.pid_file.truncate()

    def write_pid(self, pid):
        self.clear_pid()
        self.pid_file.write(str(pid) + '\n')
        self.pid_file.flush()

    def alive(self):
        self.refresh()
        if self.locked:
            return False

        pid = self.read_pid()
        try:
            os.kill(pid, 0)
            return True
        except OSError as e:
            raise Exception('Signaling pid %s failed: %s' % (pid, e))

    def read_pid(self):
        assert not self.locked, 'pid file is locked by us'
        self.pid_file.seek(0)
        line = self.pid_file.readline().strip()
        if len(line) == 0:
            raise Exception("Pid file '%s' is empty" % self.path)

        try:
            pid = int(line)
        except ValueError:
            raise Exception("Pid file '%s' contains garbage: %s" % (self.path, line))
        if pid <= 0:
            raise Exception("Pid file '%s' contains an invalid pid: %s" % (self.path, pid))
        return pid


def redirect_stdin_to_devnull():
    """Redirect stdin to /dev/null"""
    fd = os.open(os.devnull, O_RDWR)
    os.dup2(fd, sys.stdin.fileno())
    os.close(fd)


def open_append(f):
    """Open a raw file descriptor in append mode"""
    # noinspection PyTypeChecker
    return os.open(f, O_WRONLY | O_APPEND | O_CREAT, 0o644)


def redirect_output(fd):
    """Redirect stdout and stderr to a file descriptor"""
    os.dup2(fd, sys.stdout.fileno())
    os.dup2(fd, sys.stderr.fileno())


def symlink_exists(p):
    """Check if symlink exists and raise if another type of file exists"""
    try:
        st = os.lstat(p)
        if not S_ISLNK(st.st_mode):
            raise Exception('Path exists and is not a symlink: %s' % p)
        return True
    except OSError as e:
        if e.errno != errno.ENOENT:
            raise
    return False


def create_symlink(source, target):
    """Create a symlink, removing the target first if it is a symlink"""
    if symlink_exists(target):
        os.remove(target)
    if exists(source):
        os.symlink(source, target)


def create_app_symlinks(options):
    """
    Symlink the 'etc' and 'plugin' directory into the data directory.

    This is needed to support programs that reference 'etc/xyz' from within
    their config files: log.levels-file=etc/log.properties
    """
    if options.etc_dir != pathjoin(options.data_dir, 'etc'):
        create_symlink(
            options.etc_dir,
            pathjoin(options.data_dir, 'etc'))

    if options.install_path != options.data_dir:
        create_symlink(
            pathjoin(options.install_path, 'plugin'),
            pathjoin(options.data_dir, 'plugin'))


def build_java_execution(options, daemon):
    if not exists(options.config_path):
        raise Exception('Config file is missing: %s' % options.config_path)
    if not exists(options.jvm_config):
        raise Exception('JVM config file is missing: %s' % options.jvm_config)
    if not exists(options.launcher_config):
        raise Exception('Launcher config file is missing: %s' % options.launcher_config)
    if options.log_levels_set and not exists(options.log_levels):
        raise Exception('Log levels file is missing: %s' % options.log_levels)

    properties = options.properties.copy()

    if exists(options.log_levels):
        properties['log.levels-file'] = options.log_levels

    if daemon:
        properties['log.path'] = options.server_log
        properties['log.enable-console'] = 'false'

    jvm_properties = load_lines(options.jvm_config)
    launcher_properties = load_properties(options.launcher_config)

    try:
        main_class = launcher_properties['main-class']
    except KeyError:
        raise Exception("Launcher config is missing 'main-class' property")

    properties['config'] = options.config_path

    system_properties = ['-D%s=%s' % i for i in properties.items()]
    classpath = pathjoin(options.install_path, 'lib', '*')

    java_binary_prefix = ''
    if os.getenv('JAVA_HOME'):
        java_binary_prefix = os.getenv('JAVA_HOME') + '/bin/'
    command = [java_binary_prefix + 'java', '-cp', classpath]
    command += jvm_properties + system_properties
    command += [main_class]
    command += options.arguments

    if options.verbose:
        print(command)
        print("")

    env = os.environ.copy()

    # set process name: https://github.com/electrum/procname
    process_name = launcher_properties.get('process-name', '')
    if len(process_name) > 0:
        system = platform.system() + '-' + platform.machine()
        shim = pathjoin(options.install_path, 'bin', 'procname', system, 'libprocname.so')
        if exists(shim):
            env['LD_PRELOAD'] = (env.get('LD_PRELOAD', '') + ':' + shim).strip()
            env['PROCNAME'] = process_name

    return command, env


def run(options):
    if options.process.alive():
        print('Already running as %s' % options.process.read_pid())
        return

    create_app_symlinks(options)
    args, env = build_java_execution(options, False)

    makedirs(options.data_dir)
    os.chdir(options.data_dir)

    options.process.write_pid(os.getpid())

    redirect_stdin_to_devnull()

    os.execvpe(args[0], args, env)


def start(options):
    if options.process.alive():
        print('Already running as %s' % options.process.read_pid())
        return

    create_app_symlinks(options)
    args, env = build_java_execution(options, True)

    makedirs(dirname(options.launcher_log))
    log = open_append(options.launcher_log)

    makedirs(options.data_dir)
    os.chdir(options.data_dir)

    pid = os.fork()
    if pid > 0:
        options.process.write_pid(pid)
        print('Started as %s' % pid)
        return

    if hasattr(os, "set_inheritable"):
        # See https://docs.python.org/3/library/os.html#inheritance-of-file-descriptors
        # Since Python 3.4
        os.set_inheritable(options.process.pid_file.fileno(), True)

    os.setsid()

    redirect_stdin_to_devnull()
    redirect_output(log)
    os.close(log)

    os.execvpe(args[0], args, env)


def _terminate(process, signal, message):
    if not process.alive():
        print('Not running')
        return

    pid = process.read_pid()

    while True:
        try:
            os.kill(pid, signal)
        except OSError as e:
            if e.errno != errno.ESRCH:
                raise Exception('Signaling pid %s failed: %s' % (pid, e))

        if not process.alive():
            process.clear_pid()
            break

        sleep(0.1)

    print('%s %s' % (message, pid))


def stop(options):
    _terminate(options.process, SIGTERM, 'Stopped')


def kill(options):
    _terminate(options.process, SIGKILL, 'Killed')


def restart(options):
    stop(options)
    start(options)


def status(options):
    if not options.process.alive():
        print('Not running')
        sys.exit(LSB_NOT_RUNNING)
    print('Running as %s' % options.process.read_pid())


def create_parser():
    parser = ArgumentParser(prog='launcher', usage='%(prog)s [options] command')

    parser.add_argument('-v', '--verbose', action='store_true', default=False, help='Run verbosely')
    parser.add_argument('--etc-dir', metavar='DIR', help='Defaults to INSTALL_PATH/etc')
    parser.add_argument('--launcher-config', metavar='FILE', help='Defaults to INSTALL_PATH/bin/launcher.properties')
    parser.add_argument('--node-config', metavar='FILE', help='Defaults to ETC_DIR/node.properties')
    parser.add_argument('--jvm-config', metavar='FILE', help='Defaults to ETC_DIR/jvm.config')
    parser.add_argument('--config', metavar='FILE', help='Defaults to ETC_DIR/config.properties')
    parser.add_argument('--log-levels-file', metavar='FILE', help='Defaults to ETC_DIR/log.properties')
    parser.add_argument('--data-dir', metavar='DIR', help='Defaults to INSTALL_PATH')
    parser.add_argument('--pid-file', metavar='FILE', help='Defaults to DATA_DIR/var/run/launcher.pid')
    parser.add_argument('--arg', action='append', metavar='ARG', dest='arguments', help='Add a program argument of the Java application')
    parser.add_argument('--launcher-log-file', metavar='FILE', help='Defaults to DATA_DIR/var/log/launcher.log (only in daemon mode)')
    parser.add_argument('--server-log-file', metavar='FILE', help='Defaults to DATA_DIR/var/log/server.log (only in daemon mode)')
    parser.add_argument('-D', action='append', metavar='NAME=VALUE', dest='properties', help='Set a Java system property')

    commands = parser.add_subparsers(title='commands')

    command_run = commands.add_parser('run', help='run %(prog)s in the foreground')
    command_run.set_defaults(command=run)

    command_start = commands.add_parser('start', help='start %(prog)s as a daemon')
    command_start.set_defaults(command=start)

    command_stop = commands.add_parser('stop', help='terminate %(prog)s (SIGTERM)')
    command_stop.set_defaults(command=stop)

    command_kill = commands.add_parser('kill', help='forcibly terminate %(prog)s (SIGKILL)')
    command_kill.set_defaults(command=kill)

    command_restart = commands.add_parser('restart', help='stop and then start %(prog)s')
    command_restart.set_defaults(command=restart)

    command_status = commands.add_parser('status', help='check if %(prog)s is running')
    command_status.set_defaults(command=status)

    return parser


def parse_properties(parser, args):
    properties = {}
    for arg in args:
        if '=' not in arg:
            parser.error('property is malformed: %s' % arg)
        key, value = [i.strip() for i in arg.split('=', 1)]
        if key == 'config':
            parser.error('cannot specify config using -D option (use --config)')
        if key == 'log.path':
            parser.error('cannot specify server log using -D option (use --server-log-file)')
        if key == 'log.levels-file':
            parser.error('cannot specify log levels using -D option (use --log-levels-file)')
        properties[key] = value
    return properties


def print_options(options):
    if options.verbose:
        for i in sorted(vars(options)):
            print("%-15s = %s" % (i, getattr(options, i)))
        print("")


class Options:
    pass


def main():
    parser = create_parser()

    args = parser.parse_args()

    if 'command' not in args:
        parser.print_help()
        sys.exit()

    try:
        install_path = find_install_path(sys.argv[0])
    except Exception as e:
        print('ERROR: %s' % e)
        sys.exit(LSB_STATUS_UNKNOWN)

    options = Options()
    options.verbose = args.verbose
    options.install_path = install_path
    options.launcher_config = realpath(args.launcher_config or pathjoin(options.install_path, 'bin/launcher.properties'))
    options.etc_dir = realpath(args.etc_dir or pathjoin(options.install_path, 'etc'))
    options.node_config = realpath(args.node_config or pathjoin(options.etc_dir, 'node.properties'))
    options.jvm_config = realpath(args.jvm_config or pathjoin(options.etc_dir, 'jvm.config'))
    options.config_path = realpath(args.config or pathjoin(options.etc_dir, 'config.properties'))
    options.log_levels = realpath(args.log_levels_file or pathjoin(options.etc_dir, 'log.properties'))
    options.log_levels_set = bool(args.log_levels_file)

    if args.node_config and not exists(options.node_config):
        parser.error('Node config file is missing: %s' % options.node_config)

    node_properties = {}
    if exists(options.node_config):
        node_properties = load_properties(options.node_config)

    data_dir = node_properties.get('node.data-dir')
    options.data_dir = realpath(args.data_dir or data_dir or options.install_path)

    options.pid_file = realpath(args.pid_file or pathjoin(options.data_dir, 'var/run/launcher.pid'))
    options.launcher_log = realpath(args.launcher_log_file or pathjoin(options.data_dir, 'var/log/launcher.log'))
    options.server_log = realpath(args.server_log_file or pathjoin(options.data_dir, 'var/log/server.log'))

    options.properties = parse_properties(parser, args.properties or {})
    for k, v in node_properties.items():
        if k not in options.properties:
            options.properties[k] = v

    options.arguments = args.arguments or []

    if options.verbose:
        print_options(options)

    options.process = Process(options.pid_file)

    try:
        args.command(options)
    except SystemExit:
        raise
    except Exception as e:
        if options.verbose:
            traceback.print_exc()
        else:
            print('ERROR: %s' % e)
        sys.exit(LSB_STATUS_UNKNOWN)


if __name__ == '__main__':
    main()
