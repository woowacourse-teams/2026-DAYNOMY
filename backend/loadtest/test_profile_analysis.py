import unittest

from analyze_profile import seconds, summarize


def event(kind, methods, **extra):
    return {"type": kind, "values": {
        "eventThread": {"javaName": "http-nio-127.0.0.1-8496-exec-1"},
        "stackTrace": {"frames": [
            {"method": {"type": {"name": owner}, "name": name}}
            for owner, name in methods
        ]},
        **extra,
    }}


class ProfileAnalysisTest(unittest.TestCase):
    def test_nested_flush_is_one_category_but_inclusive_methods_overlap(self):
        result = summarize([event("jdk.ExecutionSample", [
            ("org/hibernate/event/internal/DefaultFlushEntityEventListener", "onFlushEntity"),
            ("org/hibernate/internal/SessionImpl", "autoFlushIfRequired"),
        ])])
        self.assertEqual(1, result["requestThreadSamples"])
        self.assertEqual("hibernate_auto_flush", result["exclusiveSampleCategories"][0]["category"])
        self.assertEqual(100, result["exclusiveSampleCategories"][0]["percent"])
        self.assertEqual(1, result["sampleCategoriesByEventType"]["jdk.ExecutionSample"][0]["samples"])
        self.assertEqual(2, len(result["topInclusiveMethods"]))

    def test_only_database_socket_reads_on_api_threads_are_counted(self):
        database = event("jdk.SocketRead", [], port=55496, duration="PT0.02S")
        browser = event("jdk.SocketRead", [], port=12345, duration="PT10S")
        management = event("jdk.SocketRead", [], port=55496, duration="PT5S")
        management["values"]["eventThread"]["javaName"] = "http-nio-127.0.0.1-8497-exec-1"
        result = summarize([database, browser, management])
        self.assertEqual(1, result["postgresSocketReads"])
        self.assertAlmostEqual(0.02, result["postgresSocketReadSeconds"])
        self.assertEqual([], result["exclusiveSampleCategories"])

    def test_duration_units(self):
        self.assertAlmostEqual(3723.004, seconds("PT1H2M3.004S"))
        with self.assertRaises(ValueError):
            seconds("3ms")


if __name__ == "__main__":
    unittest.main()
