import vmprof

import os
import six
from _prof_imports import TreeStats
import tempfile
import shutil


class VmProfProfile(object):
    """ Wrapper class that represents VmProf Python profiling backend with API matching
        the cProfile.
    """

    def __init__(self):
        self.stats = None
        self.basepath = None
        self.file = None
        self.is_enabled = False

    def runcall(self, func, *args, **kw):
        self.enable()
        try:
            return func(*args, **kw)
        finally:
            self.disable()

    def enable(self):
        if not self.is_enabled:
            if not os.path.exists(self.basepath):
                os.makedirs(self.basepath)
            self.file = tempfile.NamedTemporaryFile(delete=False, dir=self.basepath)
            print("Writing snapshot to %s" % self.file.name)
            vmprof.enable(self.file.fileno())
            self.is_enabled = True

    def disable(self):
        if self.is_enabled:
            vmprof.disable()
            self.file.close()
            self.is_enabled = False

    def create_stats(self):
        return None

    def getstats(self):
        self.create_stats()

        return self.stats

    def dump_stats(self, file):
        shutil.copyfile(self.file.name, file)

    def _walk_tree(self, parent, node, callback):
        tree = callback(parent, node)
        for c in six.itervalues(node.children):
            self._walk_tree(node, c, callback)
        return tree

    def tree_stats_to_response(self, filename, response):
        tree_stats_to_response(filename, response)

    def snapshot_extension(self):
        return '.prof'


def _walk_tree(parent, node, callback):
    tree = callback(parent, node)
    for c in six.itervalues(node.children):
        _walk_tree(tree, c, callback)
    return tree


def tree_stats_to_response(filename, response):
    stats = vmprof.read_profile(filename)

    tree = stats.get_tree()

    def convert(parent, node):
        tstats = TreeStats()
        tstats.name = node.name
        tstats.count = node.count
        tstats.children = []

        if parent is not None:
            if parent.children is None:
                parent.children = []
            parent.children.append(tstats)

        return tstats

    response.tree_stats = _walk_tree(None, tree, convert)
