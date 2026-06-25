using ClosedXML.Excel;
using WfhDiary.Api.Data;

namespace WfhDiary.Api.Services;

public sealed class XlsxExportService
{
    private const int HeaderRow = 5;
    private const int FirstDataRow = 6;
    private const decimal WfhRate = 0.67m;

    private static readonly XLColor HeaderDark = XLColor.FromHtml("#12262F");
    private static readonly XLColor InputBlue = XLColor.FromHtml("#DEEAF6");
    private static readonly XLColor HomeGreen = XLColor.FromHtml("#70AD47");
    private static readonly XLColor OfficeBlue = XLColor.FromHtml("#81D4FA");
    private static readonly XLColor LeaveGray = XLColor.FromHtml("#E0E0E0");

    public byte[] Export(UserSettingsEntity settings, DiaryEntity diary, IReadOnlyList<DayEntryEntity> days)
    {
        using var workbook = new XLWorkbook();
        var sheet = workbook.Worksheets.Add("Diary");

        var fyLabel = $"{diary.FyStartDate.Year}/{diary.FyEndDate.Year % 100:D2}";
        sheet.Cell(1, 1).Value = $"{fyLabel} Working from Home Diary";
        sheet.Cell(1, 1).Style.Font.Bold = true;
        sheet.Cell(1, 1).Style.Font.FontSize = 16;

        sheet.Cell(2, 1).Value = "Name:";
        sheet.Cell(2, 1).Style.Font.Bold = true;
        sheet.Cell(2, 2).Value = settings.DisplayName;

        WriteColumnHeaders(sheet);

        var dayMap = days.ToDictionary(d => d.Date);
        var row = FirstDataRow;
        foreach (var date in FinancialYearHelper.EachDay(diary.FyStartDate, diary.FyEndDate))
        {
            dayMap.TryGetValue(date, out var entry);
            WriteDayRow(sheet, row, date, entry);
            row++;
        }

        var lastDataRow = row - 1;
        WriteSummary(sheet, lastDataRow);
        ApplyLayout(sheet, lastDataRow);

        using var stream = new MemoryStream();
        workbook.SaveAs(stream);
        return stream.ToArray();
    }

    private static void WriteColumnHeaders(IXLWorksheet sheet)
    {
        var headers = new[]
        {
            "Day",
            "Date",
            "Started",
            "Finished",
            "Break (hrs)",
            "Hours",
            "Description",
            "Home/Office",
        };

        for (var col = 1; col <= headers.Length; col++)
        {
            var cell = sheet.Cell(HeaderRow, col);
            cell.Value = headers[col - 1];
            cell.Style.Fill.BackgroundColor = HeaderDark;
            cell.Style.Font.FontColor = XLColor.White;
            cell.Style.Font.Bold = true;
            cell.Style.Alignment.Horizontal = XLAlignmentHorizontalValues.Center;
        }
    }

    private static void WriteDayRow(IXLWorksheet sheet, int row, DateOnly date, DayEntryEntity? entry)
    {
        sheet.Cell(row, 1).Value = date.ToString("dddd");
        var dateCell = sheet.Cell(row, 2);
        dateCell.Value = date.ToDateTime(TimeOnly.MinValue);
        dateCell.Style.DateFormat.Format = "ddd d mmm yyyy";

        StyleInputCell(sheet.Cell(row, 3));
        StyleInputCell(sheet.Cell(row, 4));
        StyleInputCell(sheet.Cell(row, 5));

        if (entry?.StartTimeMinutes is int start)
            sheet.Cell(row, 3).Value = TimeHelpers.MinutesToExcelDecimal(start);
        if (entry?.FinishTimeMinutes is int finish)
            sheet.Cell(row, 4).Value = TimeHelpers.MinutesToExcelDecimal(finish);
        if (entry?.BreakHours is decimal breakHours)
            sheet.Cell(row, 5).Value = (double)breakHours;

        sheet.Cell(row, 3).Style.NumberFormat.Format = "h:mm AM/PM";
        sheet.Cell(row, 4).Style.NumberFormat.Format = "h:mm AM/PM";
        sheet.Cell(row, 5).Style.NumberFormat.Format = "0.00";

        var hasTimes = entry?.StartTimeMinutes is not null && entry.FinishTimeMinutes is not null;
        if (hasTimes)
        {
            sheet.Cell(row, 6).FormulaA1 = $"(D{row}-C{row})*24-E{row}";
        }
        else if (entry?.Description == "LEAVE")
        {
            sheet.Cell(row, 6).Value = 0;
        }

        sheet.Cell(row, 6).Style.NumberFormat.Format = "0.00";
        sheet.Cell(row, 7).Value = entry?.Description ?? "";
        sheet.Cell(row, 8).Value = entry?.Location ?? "";

        StyleLocationCell(sheet.Cell(row, 8), entry?.Location, entry?.Description);
    }

    private static void StyleInputCell(IXLCell cell)
    {
        cell.Style.Fill.BackgroundColor = InputBlue;
    }

    private static void StyleLocationCell(IXLCell cell, string? location, string? description)
    {
        if (string.Equals(location, "Home", StringComparison.OrdinalIgnoreCase))
        {
            cell.Style.Fill.BackgroundColor = HomeGreen;
            cell.Style.Font.FontColor = XLColor.White;
            cell.Style.Font.Bold = true;
        }
        else if (string.Equals(location, "Office", StringComparison.OrdinalIgnoreCase))
        {
            cell.Style.Fill.BackgroundColor = OfficeBlue;
            cell.Style.Font.Bold = true;
        }
        else if (string.Equals(description, "LEAVE", StringComparison.OrdinalIgnoreCase))
        {
            cell.Style.Fill.BackgroundColor = LeaveGray;
        }
    }

    private static void WriteSummary(IXLWorksheet sheet, int lastDataRow)
    {
        var summaryRow = lastDataRow + 2;
        var range = $"{FirstDataRow}:{lastDataRow}";

        WriteSummaryRow(sheet, summaryRow, "Total hours worked", $"SUM(F{range})");
        WriteSummaryRow(sheet, summaryRow + 1, "Home hours", $"SUMIF(H{range},\"Home\",F{range})");
        WriteSummaryRow(sheet, summaryRow + 2, "Claimable WFH (Home hours × $0.67)", $"B{summaryRow + 1}*{WfhRate.ToString(System.Globalization.CultureInfo.InvariantCulture)}", currency: true);
        WriteSummaryRow(sheet, summaryRow + 3, "Office hours", $"SUMIF(H{range},\"Office\",F{range})");
        WriteSummaryRow(sheet, summaryRow + 4, "Days at home", $"COUNTIF(H{range},\"Home\")", integer: true);
        WriteSummaryRow(sheet, summaryRow + 5, "Days in office", $"COUNTIF(H{range},\"Office\")", integer: true);
    }

    private static void WriteSummaryRow(
        IXLWorksheet sheet,
        int row,
        string label,
        string formula,
        bool currency = false,
        bool integer = false)
    {
        sheet.Cell(row, 1).Value = label;
        sheet.Cell(row, 1).Style.Font.Bold = true;
        sheet.Cell(row, 2).FormulaA1 = formula;
        sheet.Cell(row, 2).Style.Font.Bold = true;
        if (currency)
            sheet.Cell(row, 2).Style.NumberFormat.Format = "$#,##0.00";
        else if (integer)
            sheet.Cell(row, 2).Style.NumberFormat.Format = "0";
        else
            sheet.Cell(row, 2).Style.NumberFormat.Format = "0.00";
    }

    private static void ApplyLayout(IXLWorksheet sheet, int lastDataRow)
    {
        sheet.Column(1).Width = 12;
        sheet.Column(2).Width = 18;
        sheet.Column(3).Width = 11;
        sheet.Column(4).Width = 11;
        sheet.Column(5).Width = 11;
        sheet.Column(6).Width = 9;
        sheet.Column(7).Width = 36;
        sheet.Column(8).Width = 12;
        sheet.SheetView.FreezeRows(HeaderRow);
        sheet.Range(HeaderRow, 1, lastDataRow, 8).SetAutoFilter();
    }
}
