using ClosedXML.Excel;
using WfhDiary.Api.Data;

namespace WfhDiary.Api.Services;

public sealed class XlsxExportService(IWebHostEnvironment env)
{
    private const int FirstDataRow = 24;

    public byte[] Export(UserSettingsEntity settings, DiaryEntity diary, IReadOnlyList<DayEntryEntity> days)
    {
        var templatePath = Path.Combine(env.ContentRootPath, "Resources", "wfh-diary-template.xlsx");
        if (!File.Exists(templatePath))
        {
            throw new FileNotFoundException("Template not found.", templatePath);
        }

        using var workbook = new XLWorkbook(templatePath);
        var sheet = workbook.Worksheet(1);

        sheet.Cell("B3").Value = settings.DisplayName;

        var dayMap = days.ToDictionary(d => d.Date);
        var row = FirstDataRow;
        foreach (var date in FinancialYearHelper.EachDay(diary.FyStartDate, diary.FyEndDate))
        {
            dayMap.TryGetValue(date, out var entry);
            sheet.Cell(row, 1).Value = date.ToString("dddd");
            sheet.Cell(row, 2).Value = date.ToDateTime(TimeOnly.MinValue).ToOADate();

            if (entry?.StartTimeMinutes is int start)
                sheet.Cell(row, 3).Value = TimeHelpers.MinutesToExcelDecimal(start);
            else
                sheet.Cell(row, 3).Clear();

            if (entry?.FinishTimeMinutes is int finish)
                sheet.Cell(row, 4).Value = TimeHelpers.MinutesToExcelDecimal(finish);
            else
                sheet.Cell(row, 4).Clear();

            if (entry?.BreakHours is decimal br)
                sheet.Cell(row, 5).Value = (double)br;
            else
                sheet.Cell(row, 5).Clear();

            var hasTimes = entry?.StartTimeMinutes is not null && entry.FinishTimeMinutes is not null;
            if (hasTimes)
            {
                var c = sheet.Cell(row, 3).Address.ColumnLetter;
                var d = sheet.Cell(row, 4).Address.ColumnLetter;
                var e = sheet.Cell(row, 5).Address.ColumnLetter;
                sheet.Cell(row, 6).FormulaA1 = $"({d}{row}-{c}{row})*24-{e}{row}";
            }
            else if (entry?.Description == "LEAVE")
            {
                sheet.Cell(row, 6).Value = 0;
            }
            else
            {
                sheet.Cell(row, 6).Clear();
            }

            sheet.Cell(row, 7).Value = entry?.Description ?? "";
            sheet.Cell(row, 8).Value = entry?.Location ?? "";
            row++;
        }

        var lastDataRow = row - 1;
        sheet.Cell("N13").FormulaA1 = $"SUM(F{FirstDataRow}:F{lastDataRow})";
        sheet.Cell("N14").FormulaA1 = $"SUMIF(H{FirstDataRow}:H{lastDataRow + 1},\"Home\",F{FirstDataRow}:F{lastDataRow + 1})";
        sheet.Cell("O14").FormulaA1 = "N14*0.67";
        sheet.Cell("N15").FormulaA1 = $"SUMIF(H{FirstDataRow}:H{lastDataRow + 1},\"Office\",F{FirstDataRow}:F{lastDataRow + 1})";
        sheet.Cell("N16").FormulaA1 = "N15+N14-N13";
        sheet.Cell("J16").FormulaA1 = "IF(N13=N14+N15,\"Working\",\"Math is wrong!\")";
        sheet.Cell("I23").FormulaA1 = $"COUNTIFS(H{FirstDataRow}:H{lastDataRow + 1},\"Home\")";
        sheet.Cell("J23").FormulaA1 = $"COUNTIFS(H{FirstDataRow}:H{lastDataRow + 1},\"Office\")";

        var footerRow = lastDataRow + 1;
        sheet.Cell(footerRow, 6).FormulaA1 = $"SUM(F{FirstDataRow}:F{lastDataRow})";
        sheet.Cell(footerRow + 1, 7).FormulaA1 = $"F{footerRow}*0.67";
        sheet.Cell(footerRow + 1, 8).FormulaA1 = $"COUNTIFS(H{FirstDataRow}:H{lastDataRow},\"Home\")";
        sheet.Cell(footerRow + 3, 8).FormulaA1 = $"COUNTIFS(H{FirstDataRow}:H{lastDataRow},\"Office\")";

        using var stream = new MemoryStream();
        workbook.SaveAs(stream);
        return stream.ToArray();
    }
}
