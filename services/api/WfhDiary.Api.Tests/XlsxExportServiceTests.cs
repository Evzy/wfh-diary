using ClosedXML.Excel;
using WfhDiary.Api.Data;
using WfhDiary.Api.Services;

namespace WfhDiary.Api.Tests;

public class XlsxExportServiceTests
{
    [Fact]
    public void Export_summary_formulas_use_valid_column_ranges()
    {
        var service = new XlsxExportService();
        var settings = new UserSettingsEntity
        {
            OwnerUserId = "test",
            DisplayName = "Test User",
        };
        var diary = new DiaryEntity
        {
            OwnerUserId = "test",
            FyYear = 2025,
            FyStartDate = new DateOnly(2025, 7, 1),
            FyEndDate = new DateOnly(2026, 6, 30),
        };
        var days = new List<DayEntryEntity>
        {
            new()
            {
                Date = new DateOnly(2025, 7, 1),
                StartTimeMinutes = 8 * 60,
                FinishTimeMinutes = 17 * 60,
                BreakHours = 1m,
                Location = "Home",
            },
        };

        var bytes = service.Export(settings, diary, days);

        using var stream = new MemoryStream(bytes);
        using var workbook = new XLWorkbook(stream);
        var sheet = workbook.Worksheet("Diary");
        var lastDataRow = 6 + FinancialYearHelper.EachDay(diary.FyStartDate, diary.FyEndDate).Count() - 1;
        var summaryRow = lastDataRow + 2;

        Assert.Equal($"SUM(F6:F{lastDataRow})", sheet.Cell(summaryRow, 2).FormulaA1);
        Assert.Equal($"SUMIF(H6:H{lastDataRow},\"Home\",F6:F{lastDataRow})", sheet.Cell(summaryRow + 1, 2).FormulaA1);
        Assert.Equal($"SUMIF(H6:H{lastDataRow},\"Office\",F6:F{lastDataRow})", sheet.Cell(summaryRow + 3, 2).FormulaA1);
        Assert.Equal($"COUNTIF(H6:H{lastDataRow},\"Home\")", sheet.Cell(summaryRow + 4, 2).FormulaA1);
        Assert.Equal($"COUNTIF(H6:H{lastDataRow},\"Office\")", sheet.Cell(summaryRow + 5, 2).FormulaA1);
    }

    [Fact]
    public void Export_day_row_hours_formula_uses_start_finish_and_break()
    {
        var service = new XlsxExportService();
        var settings = new UserSettingsEntity { OwnerUserId = "test", DisplayName = "Test User" };
        var diary = new DiaryEntity
        {
            OwnerUserId = "test",
            FyYear = 2025,
            FyStartDate = new DateOnly(2025, 7, 1),
            FyEndDate = new DateOnly(2025, 7, 7),
        };
        var days = new List<DayEntryEntity>
        {
            new()
            {
                Date = new DateOnly(2025, 7, 1),
                StartTimeMinutes = 8 * 60,
                FinishTimeMinutes = 17 * 60,
                BreakHours = 1m,
                Location = "Home",
            },
        };

        var bytes = service.Export(settings, diary, days);

        using var stream = new MemoryStream(bytes);
        using var workbook = new XLWorkbook(stream);
        var sheet = workbook.Worksheet("Diary");

        Assert.Equal("(D6-C6)*24-E6", sheet.Cell(6, 6).FormulaA1);
    }
}
